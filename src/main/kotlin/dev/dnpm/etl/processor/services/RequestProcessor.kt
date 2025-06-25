/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2025  Comprehensive Cancer Center Mainfranken, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.dnpm.etl.processor.services

import com.fasterxml.jackson.databind.ObjectMapper
import de.ukw.ccc.bwhc.dto.MtbFile
import dev.dnpm.etl.processor.*
import dev.dnpm.etl.processor.config.AppConfigProperties
import dev.dnpm.etl.processor.consent.GicsConsentService
import dev.dnpm.etl.processor.consent.TtpConsentStatus
import dev.dnpm.etl.processor.monitoring.Report
import dev.dnpm.etl.processor.monitoring.Request
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.dnpm.etl.processor.monitoring.RequestType
import dev.dnpm.etl.processor.output.*
import dev.dnpm.etl.processor.pseudonym.PseudonymizeService
import dev.dnpm.etl.processor.pseudonym.anonymizeContentWith
import dev.dnpm.etl.processor.pseudonym.pseudonymizeWith
import dev.pcvolkmer.mv64e.mtb.ConsentProvision
import dev.pcvolkmer.mv64e.mtb.ModelProjectConsent
import dev.pcvolkmer.mv64e.mtb.ModelProjectConsentPurpose
import dev.pcvolkmer.mv64e.mtb.Mtb
import dev.pcvolkmer.mv64e.mtb.MvhMetadata
import dev.pcvolkmer.mv64e.mtb.Provision
import org.apache.commons.codec.binary.Base32
import org.apache.commons.codec.digest.DigestUtils
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Consent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*

@Service
class RequestProcessor(
    private val pseudonymizeService: PseudonymizeService,
    private val transformationService: TransformationService,
    private val sender: MtbFileSender,
    private val requestService: RequestService,
    private val objectMapper: ObjectMapper,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val appConfigProperties: AppConfigProperties,
    private val gicsConsentService: GicsConsentService?
) {

    fun processMtbFile(mtbFile: MtbFile) {
        processMtbFile(mtbFile, randomRequestId())
    }

    fun processMtbFile(mtbFile: MtbFile, requestId: RequestId) {
        val pid = PatientId(mtbFile.patient.id)
        mtbFile pseudonymizeWith pseudonymizeService
        mtbFile anonymizeContentWith pseudonymizeService
        val request = BwhcV1MtbFileRequest(requestId, transformationService.transform(mtbFile))
        saveAndSend(request, pid)
    }

    fun processMtbFile(mtbFile: Mtb) {
        processMtbFile(mtbFile, randomRequestId())
    }

    fun processMtbFile(mtbFile: Mtb, requestId: RequestId) {
        val pid = PatientId(mtbFile.patient.id)

        addConsentToMtb(mtbFile)
        mtbFile pseudonymizeWith pseudonymizeService
        mtbFile anonymizeContentWith pseudonymizeService
        val request = DnpmV2MtbFileRequest(requestId, transformationService.transform(mtbFile))
        saveAndSend(request, pid)
    }

    fun addConsentToMtb(mtbFile: Mtb) {
        if (gicsConsentService == null) return
        // init metadata if necessary
        if (mtbFile.metadata == null) {
            val mvhMetadata = MvhMetadata.builder().build();
            mtbFile.metadata = mvhMetadata
            if (mtbFile.metadata.researchConsents == null) {
                mtbFile.metadata.researchConsents = mutableListOf()
            }
            if (mtbFile.metadata.modelProjectConsent == null) {
                mtbFile.metadata.modelProjectConsent = ModelProjectConsent()
                mtbFile.metadata.modelProjectConsent.provisions = mutableListOf()
            }
        }

        // fixme Date should be extracted from mtbFile
        val consentGnomeDe =
            gicsConsentService.getGenomDeConsent(mtbFile.patient.id, Date.from(Instant.now()))
        addGenomeDbProvisions(mtbFile, consentGnomeDe)

        // fixme Date should be extracted from mtbFile
        val broadConsent =
            gicsConsentService.getBroadConsent(mtbFile.patient.id, Date.from(Instant.now()))
        embedBroadConsentResources(mtbFile, broadConsent)
    }

    fun embedBroadConsentResources(
        mtbFile: Mtb,
        broadConsent: Bundle
    ) {
        broadConsent.entry.forEach { it ->
            mtbFile.metadata.researchConsents.add(mapOf(it.resource.id to it as IBaseResource))
        }
    }

    fun addGenomeDbProvisions(
        mtbFile: Mtb,
        consentGnomeDe: Bundle
    ) {
        consentGnomeDe.entry.forEach { it ->
            {
                val consent = it.resource as Consent
                val provisionComponent = consent.provision.provision.firstOrNull()
                val provisionCode =
                    provisionComponent?.code?.firstOrNull()?.coding?.firstOrNull()?.code
                var isValidCode = true
                if (provisionCode != null) {
                    var modelProjectConsentPurpose: ModelProjectConsentPurpose =
                        ModelProjectConsentPurpose.SEQUENCING
                    if (provisionCode == "Teilnahme") {
                        modelProjectConsentPurpose = ModelProjectConsentPurpose.SEQUENCING
                    } else if (provisionCode == "Fallidentifizierung") {
                        modelProjectConsentPurpose = ModelProjectConsentPurpose.CASE_IDENTIFICATION
                    } else if (provisionCode == "Rekontaktierung") {
                        modelProjectConsentPurpose = ModelProjectConsentPurpose.REIDENTIFICATION
                    } else {
                        isValidCode = false
                    }
                    if (isValidCode) mtbFile.metadata.modelProjectConsent.provisions.add(
                        Provision.builder().type(
                            ConsentProvision.forValue(provisionComponent.type.name)
                        ).date(provisionComponent.period.start).purpose(
                            modelProjectConsentPurpose
                        ).build()
                    )
                }
            }
        }
    }

    private fun <T> saveAndSend(request: MtbFileRequest<T>, pid: PatientId) {
        requestService.save(
            Request(
                request.requestId,
                request.patientPseudonym(),
                pid,
                fingerprint(request),
                RequestType.MTB_FILE,
                RequestStatus.UNKNOWN
            )
        )

        if (appConfigProperties.duplicationDetection && isDuplication(request)) {
            applicationEventPublisher.publishEvent(
                ResponseEvent(
                    request.requestId,
                    Instant.now(),
                    RequestStatus.DUPLICATION
                )
            )
            return
        }

        val responseStatus = sender.send(request)

        applicationEventPublisher.publishEvent(
            ResponseEvent(
                request.requestId,
                Instant.now(),
                responseStatus.status,
                when (responseStatus.status) {
                    RequestStatus.ERROR, RequestStatus.WARNING -> Optional.of(responseStatus.body)
                    else -> Optional.empty()
                }
            )
        )
    }

    private fun <T> isDuplication(pseudonymizedMtbFileRequest: MtbFileRequest<T>): Boolean {
        val patientPseudonym = when (pseudonymizedMtbFileRequest) {
            is BwhcV1MtbFileRequest -> PatientPseudonym(pseudonymizedMtbFileRequest.content.patient.id)
            is DnpmV2MtbFileRequest -> PatientPseudonym(pseudonymizedMtbFileRequest.content.patient.id)
        }

        val lastMtbFileRequestForPatient =
            requestService.lastMtbFileRequestForPatientPseudonym(patientPseudonym)
        val isLastRequestDeletion =
            requestService.isLastRequestWithKnownStatusDeletion(patientPseudonym)

        return null != lastMtbFileRequestForPatient
                && !isLastRequestDeletion
                && lastMtbFileRequestForPatient.fingerprint == fingerprint(
            pseudonymizedMtbFileRequest
        )
    }

    fun processDeletion(patientId: PatientId, isConsented: TtpConsentStatus) {
        processDeletion(patientId, randomRequestId(), isConsented)
    }

    fun processDeletion(patientId: PatientId, requestId: RequestId, isConsented: TtpConsentStatus) {
        try {
            val patientPseudonym = pseudonymizeService.patientPseudonym(patientId)

            val requestStatus: RequestStatus = when (isConsented) {
                TtpConsentStatus.CONSENT_MISSING_OR_REJECTED -> RequestStatus.NO_CONSENT
                TtpConsentStatus.FAILED_TO_ASK -> RequestStatus.ERROR
                TtpConsentStatus.CONSENTED, TtpConsentStatus.UNKNOWN_CHECK_FILE -> RequestStatus.UNKNOWN
            }

            requestService.save(
                Request(
                    requestId,
                    patientPseudonym,
                    patientId,
                    fingerprint(patientPseudonym.value),
                    RequestType.DELETE,
                    requestStatus
                )
            )

            val responseStatus = sender.send(DeleteRequest(requestId, patientPseudonym))

            applicationEventPublisher.publishEvent(
                ResponseEvent(
                    requestId,
                    Instant.now(),
                    responseStatus.status,
                    when (responseStatus.status) {
                        RequestStatus.WARNING, RequestStatus.ERROR -> Optional.of(responseStatus.body)
                        else -> Optional.empty()
                    }
                )
            )

        } catch (e: Exception) {
            requestService.save(
                Request(
                    uuid = requestId,
                    patientPseudonym = emptyPatientPseudonym(),
                    pid = patientId,
                    fingerprint = Fingerprint.empty(),
                    status = RequestStatus.ERROR,
                    type = RequestType.DELETE,
                    report = Report("Fehler bei der Pseudonymisierung")
                )
            )
        }
    }

    private fun <T> fingerprint(request: MtbFileRequest<T>): Fingerprint {
        return when (request) {
            is BwhcV1MtbFileRequest -> fingerprint(objectMapper.writeValueAsString(request.content))
            is DnpmV2MtbFileRequest -> fingerprint(objectMapper.writeValueAsString(request.content))
        }
    }

    private fun fingerprint(s: String): Fingerprint {
        return Fingerprint(
            Base32().encodeAsString(DigestUtils.sha256(s))
                .replace("=", "")
                .lowercase()
        )
    }

}
