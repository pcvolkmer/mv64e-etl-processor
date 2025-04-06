/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2023  Comprehensive Cancer Center Mainfranken, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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
import dev.dnpm.etl.processor.monitoring.Report
import dev.dnpm.etl.processor.monitoring.Request
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.dnpm.etl.processor.monitoring.RequestType
import dev.dnpm.etl.processor.output.BwhcV1MtbFileRequest
import dev.dnpm.etl.processor.output.DeleteRequest
import dev.dnpm.etl.processor.output.MtbFileSender
import dev.dnpm.etl.processor.pseudonym.PseudonymizeService
import dev.dnpm.etl.processor.pseudonym.anonymizeContentWith
import dev.dnpm.etl.processor.pseudonym.pseudonymizeWith
import org.apache.commons.codec.binary.Base32
import org.apache.commons.codec.digest.DigestUtils
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
    private val appConfigProperties: AppConfigProperties
) {

    fun processMtbFile(mtbFile: MtbFile) {
        processMtbFile(mtbFile, randomRequestId())
    }

    fun processMtbFile(mtbFile: MtbFile, requestId: RequestId) {
        val pid = PatientId(mtbFile.patient.id)

        mtbFile pseudonymizeWith pseudonymizeService
        mtbFile anonymizeContentWith pseudonymizeService

        val request = BwhcV1MtbFileRequest(requestId, transformationService.transform(mtbFile))

        val patientPseudonym = PatientPseudonym(request.content.patient.id)

        requestService.save(
            Request(
                requestId,
                patientPseudonym,
                pid,
                fingerprint(request.content),
                RequestType.MTB_FILE,
                RequestStatus.UNKNOWN
            )
        )

        if (appConfigProperties.duplicationDetection && isDuplication(mtbFile)) {
            applicationEventPublisher.publishEvent(
                ResponseEvent(
                    requestId,
                    Instant.now(),
                    RequestStatus.DUPLICATION
                )
            )
            return
        }

        val responseStatus = sender.send(request)

        applicationEventPublisher.publishEvent(
            ResponseEvent(
                requestId,
                Instant.now(),
                responseStatus.status,
                when (responseStatus.status) {
                    RequestStatus.ERROR, RequestStatus.WARNING -> Optional.of(responseStatus.body)
                    else -> Optional.empty()
                }
            )
        )
    }

    private fun isDuplication(pseudonymizedMtbFile: MtbFile): Boolean {
        val patientPseudonym = PatientPseudonym(pseudonymizedMtbFile.patient.id)

        val lastMtbFileRequestForPatient =
            requestService.lastMtbFileRequestForPatientPseudonym(patientPseudonym)
        val isLastRequestDeletion = requestService.isLastRequestWithKnownStatusDeletion(patientPseudonym)

        return null != lastMtbFileRequestForPatient
                && !isLastRequestDeletion
                && lastMtbFileRequestForPatient.fingerprint == fingerprint(pseudonymizedMtbFile)
    }

    fun processDeletion(patientId: PatientId) {
        processDeletion(patientId, randomRequestId())
    }

    fun processDeletion(patientId: PatientId, requestId: RequestId) {
        try {
            val patientPseudonym = pseudonymizeService.patientPseudonym(patientId)

            requestService.save(
                Request(
                    requestId,
                    patientPseudonym,
                    patientId,
                    fingerprint(patientPseudonym.value),
                    RequestType.DELETE,
                    RequestStatus.UNKNOWN
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

    private fun fingerprint(mtbFile: MtbFile): Fingerprint {
        return fingerprint(objectMapper.writeValueAsString(mtbFile))
    }

    private fun fingerprint(s: String): Fingerprint {
        return Fingerprint(
            Base32().encodeAsString(DigestUtils.sha256(s))
                .replace("=", "")
                .lowercase()
        )
    }

}
