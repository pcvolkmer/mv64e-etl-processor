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

package dev.dnpm.etl.processor.web

import com.fasterxml.jackson.databind.ObjectMapper
import de.ukw.ccc.bwhc.dto.MtbFile
import dev.dnpm.etl.processor.monitoring.Request
import dev.dnpm.etl.processor.monitoring.RequestRepository
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.dnpm.etl.processor.output.MtbFileSender
import dev.dnpm.etl.processor.pseudonym.PseudonymizeService
import org.apache.commons.codec.binary.Base32
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class MtbFileController(
    private val pseudonymizeService: PseudonymizeService,
    private val senders: List<MtbFileSender>,
    private val requestRepository: RequestRepository,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(MtbFileController::class.java)

    @PostMapping(path = ["/mtbfile"])
    fun mtbFile(@RequestBody mtbFile: MtbFile): ResponseEntity<Void> {
        val pid = mtbFile.patient.id
        val pseudonymized = pseudonymizeService.pseudonymize(mtbFile)

        val lastRequestForPatient =
            requestRepository.findByPatientIdOrderByProcessedAtDesc(pseudonymized.patient.id).firstOrNull()

        if (null != lastRequestForPatient && lastRequestForPatient.fingerprint == fingerprint(mtbFile)) {
            requestRepository.save(
                Request(
                    patientId = pseudonymized.patient.id,
                    pid = pid,
                    fingerprint = fingerprint(mtbFile),
                    status = RequestStatus.DUPLICATION
                )
            )
            return ResponseEntity.noContent().build()
        }

        val responses = senders.map {
            val responseStatus = it.send(pseudonymized)
            if (responseStatus == MtbFileSender.ResponseStatus.SUCCESS || responseStatus == MtbFileSender.ResponseStatus.WARNING) {
                logger.info(
                    "Sent file for Patient '{}' using '{}'",
                    pseudonymized.patient.id,
                    it.javaClass.simpleName
                )
            } else {
                logger.error(
                    "Error sending file for Patient '{}' using '{}'",
                    pseudonymized.patient.id,
                    it.javaClass.simpleName
                )
            }
            responseStatus
        }

        val requestStatus = if (responses.contains(MtbFileSender.ResponseStatus.ERROR)) {
            RequestStatus.ERROR
        } else if (responses.contains(MtbFileSender.ResponseStatus.WARNING)) {
            RequestStatus.WARNING
        } else if (responses.contains(MtbFileSender.ResponseStatus.SUCCESS)) {
            RequestStatus.SUCCESS
        } else {
            RequestStatus.UNKNOWN
        }

        requestRepository.save(
            Request(
                patientId = pseudonymized.patient.id,
                pid = pid,
                fingerprint = fingerprint(mtbFile),
                status = requestStatus
            )
        )

        return if (requestStatus == RequestStatus.ERROR) {
            ResponseEntity.unprocessableEntity().build()
        } else {
            ResponseEntity.noContent().build()
        }
    }

    private fun fingerprint(mtbFile: MtbFile): String {
        return Base32().encodeAsString(DigestUtils.sha256(objectMapper.writeValueAsString(mtbFile)))
            .replace("=", "")
            .lowercase()
    }

}