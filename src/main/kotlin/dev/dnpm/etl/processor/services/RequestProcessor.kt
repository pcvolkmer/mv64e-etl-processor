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
import dev.dnpm.etl.processor.consent.ConsentDomain
import dev.dnpm.etl.processor.consent.ICheckConsent
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
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Consent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.io.IOException
import java.lang.RuntimeException
import java.time.Clock
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
    private val consentService: ICheckConsent?
) {

    private var logger: Logger = LoggerFactory.getLogger("RequestProcessor")
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

    /**
     * In case an instance of {@link  ICheckConsent} is active, consent will be embedded and checked.
     *
     * Logik:
     *  * <c>true</c> IF consent check is disabled.
     *  * <c>true</c> IF broad consent (BC) has been given.
     *  * <c>true</c> BC has been asked AND declined but genomDe consent has been consented.
     *  * ELSE <c>false</c> is returned.
     *
     * @param mtbFile File v2 (will be enriched with consent data)
     * @return true if consent is given
     *
     */
    fun consentGatedCheck(mtbFile: Mtb): Boolean {
        if (consentService == null) {
            // consent check seems to be disabled
            return true
        }

        initMetaDataAtMtbFile(mtbFile)

        val personIdentifierValue = extractPatientIdentifier(mtbFile)
        val requestDate = Date.from(Instant.now(Clock.systemUTC()))

        // 1. Broad consent Entry exists?
        // 1.1. -> yes and research consent is given -> send mtb file
        // 1.2. -> no -> return status error - consent has not been asked
        // 2. ->  Broad consent found but rejected -> is GenomDe consent provision 'sequencing' given?
        // 2.1 -> yes -> send mtb file
        // 2.2 -> no ->  warn/info no consent given

        /*
         * broad consent
         */
        val broadConsent = consentService.getBroadConsent(personIdentifierValue, requestDate)
        val broadConsentHasBeenAsked = !broadConsent.entry.isEmpty()

        // fast exit - if patient has not been asked, we can skip and exit
        if (!broadConsentHasBeenAsked) return false

        val genomeDeConsent = consentService.getGenomDeConsent(
            personIdentifierValue, requestDate
        )

        addGenomeDbProvisions(mtbFile, genomeDeConsent)
        embedBroadConsentResources(mtbFile, broadConsent)

        val broadConsentStatus = consentService.getProvisionTypeByPolicyCode(
            broadConsent,
            requestDate,
            ConsentDomain.BroadConsent
        )

        val genomDeSequencingStatus = consentService.getProvisionTypeByPolicyCode(
            genomeDeConsent, requestDate,
            ConsentDomain.Modelvorhaben64e
        )

        if (Consent.ConsentProvisionType.PERMIT == broadConsentStatus) return true
        if (Consent.ConsentProvisionType.DENY == broadConsentStatus && Consent.ConsentProvisionType.PERMIT == genomDeSequencingStatus) return true
        if (Consent.ConsentProvisionType.NULL == broadConsentStatus) {
            // bc not asked
            return false
        }

        return false
    }


    private fun initMetaDataAtMtbFile(mtbFile: Mtb) {
        // init metadata if necessary
        if (mtbFile.metadata == null) {
            val mvhMetadata = MvhMetadata.builder().build()
            mtbFile.metadata = mvhMetadata
            if (mtbFile.metadata.researchConsents == null) {
                mtbFile.metadata.researchConsents = mutableListOf()
            }
            if (mtbFile.metadata.modelProjectConsent == null) {
                mtbFile.metadata.modelProjectConsent = ModelProjectConsent()
                mtbFile.metadata.modelProjectConsent.provisions = mutableListOf()
            }
        }
    }

    fun processMtbFile(mtbFile: Mtb, requestId: RequestId) {
        val pid = PatientId(extractPatientIdentifier(mtbFile))

        if (consentGatedCheck(mtbFile)) {
            mtbFile pseudonymizeWith pseudonymizeService
            mtbFile anonymizeContentWith pseudonymizeService
            val request = DnpmV2MtbFileRequest(requestId, transformationService.transform(mtbFile))
            saveAndSend(request, pid)
        } else {
            logger.warn("consent check failed file will not be processed further!")
            applicationEventPublisher.publishEvent(
                ResponseEvent(
                    requestId, Instant.now(), RequestStatus.NO_CONSENT
                )
            )
        }
    }


    fun embedBroadConsentResources(
        mtbFile: Mtb, broadConsent: Bundle
    ) {
        broadConsent.entry.forEach { it ->
            mtbFile.metadata.researchConsents.add(mapOf(it.resource.id to it.resource as Consent))
        }
    }

    fun addGenomeDbProvisions(
        mtbFile: Mtb, consentGnomeDe: Bundle
    ) {
        consentGnomeDe.entry.forEach { it ->
            {
                val consentFhirResource = it.resource as Consent

                // we expect only one provision in collection, therefore get first or none
                val provisionComponent = consentFhirResource.provision.provision.firstOrNull()
                val provisionCode =
                    provisionComponent?.code?.firstOrNull()?.coding?.firstOrNull()?.code

                if (provisionCode != null) {
                    try {
                        val modelProjectConsentPurpose: ModelProjectConsentPurpose =
                            ModelProjectConsentPurpose.valueOf(provisionCode)
                        mtbFile.metadata.modelProjectConsent.provisions.add(
                            Provision.builder().type(
                                ConsentProvision.forValue(provisionComponent.type.name)
                            ).date(provisionComponent.period.start).purpose(
                                modelProjectConsentPurpose
                            ).build()
                        )
                    } catch (ioe: IOException) {
                        logger.error(
                            "provision code '$provisionCode' is unknown and cannot be mapped.",
                            ioe.toString()
                        )
                    }
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
                    request.requestId, Instant.now(), RequestStatus.DUPLICATION
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

        return null != lastMtbFileRequestForPatient && !isLastRequestDeletion && lastMtbFileRequestForPatient.fingerprint == fingerprint(
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
                TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED, TtpConsentStatus.BROAD_CONSENT_MISSING, TtpConsentStatus.BROAD_CONSENT_REJECTED -> RequestStatus.NO_CONSENT
                TtpConsentStatus.FAILED_TO_ASK -> RequestStatus.ERROR
                TtpConsentStatus.BROAD_CONSENT_GIVEN, TtpConsentStatus.UNKNOWN_CHECK_FILE -> RequestStatus.UNKNOWN
                TtpConsentStatus.GENOM_DE_CONSENT_SEQUENCING_PERMIT, TtpConsentStatus.GENOM_DE_CONSENT_MISSING, TtpConsentStatus.GENOM_DE_SEQUENCING_REJECTED -> {
                    throw RuntimeException("processDelete should never deal with '" + isConsented.name + "' consent status. This is a bug and need to be fixed!")
                }
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
                    requestId, Instant.now(), responseStatus.status, when (responseStatus.status) {
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
            Base32().encodeAsString(DigestUtils.sha256(s)).replace("=", "").lowercase()
        )
    }

}

private fun extractPatientIdentifier(mtbFile: Mtb): String = mtbFile.patient.id
