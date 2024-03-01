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
import dev.dnpm.etl.processor.config.AppConfigProperties
import dev.dnpm.etl.processor.monitoring.Report
import dev.dnpm.etl.processor.monitoring.Request
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.dnpm.etl.processor.monitoring.RequestType
import dev.dnpm.etl.processor.output.MtbFileSender
import dev.dnpm.etl.processor.pseudonym.PseudonymizeService
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
        val requestId = UUID.randomUUID().toString()
        val pid = mtbFile.patient.id

        mtbFile pseudonymizeWith pseudonymizeService

        val request = MtbFileSender.MtbFileRequest(requestId, transformationService.transform(mtbFile))

        requestService.save(
            Request(
                uuid = requestId,
                patientId = request.mtbFile.patient.id,
                pid = pid,
                fingerprint = fingerprint(request.mtbFile),
                status = RequestStatus.UNKNOWN,
                type = RequestType.MTB_FILE
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
                    RequestStatus.WARNING -> Optional.of(responseStatus.body)
                    else -> Optional.empty()
                }
            )
        )
    }

    private fun isDuplication(pseudonymizedMtbFile: MtbFile): Boolean {
        val lastMtbFileRequestForPatient =
            requestService.lastMtbFileRequestForPatientPseudonym(pseudonymizedMtbFile.patient.id)
        val isLastRequestDeletion = requestService.isLastRequestWithKnownStatusDeletion(pseudonymizedMtbFile.patient.id)

        return null != lastMtbFileRequestForPatient
                && !isLastRequestDeletion
                && lastMtbFileRequestForPatient.fingerprint == fingerprint(pseudonymizedMtbFile)
    }

    fun processDeletion(patientId: String) {
        val requestId = UUID.randomUUID().toString()

        try {
            val patientPseudonym = pseudonymizeService.patientPseudonym(patientId)

            requestService.save(
                Request(
                    uuid = requestId,
                    patientId = patientPseudonym,
                    pid = patientId,
                    fingerprint = fingerprint(patientPseudonym),
                    status = RequestStatus.UNKNOWN,
                    type = RequestType.DELETE
                )
            )

            val responseStatus = sender.send(MtbFileSender.DeleteRequest(requestId, patientPseudonym))

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
                    patientId = "???",
                    pid = patientId,
                    fingerprint = "",
                    status = RequestStatus.ERROR,
                    type = RequestType.DELETE,
                    report = Report("Fehler bei der Pseudonymisierung")
                )
            )
        }
    }

    private fun fingerprint(mtbFile: MtbFile): String {
        return fingerprint(objectMapper.writeValueAsString(mtbFile))
    }

    private fun fingerprint(s: String): String {
        return Base32().encodeAsString(DigestUtils.sha256(s))
            .replace("=", "")
            .lowercase()
    }

}