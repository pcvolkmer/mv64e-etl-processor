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
import dev.dnpm.etl.processor.monitoring.Report
import dev.dnpm.etl.processor.monitoring.Request
import dev.dnpm.etl.processor.monitoring.RequestRepository
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.dnpm.etl.processor.output.MtbFileSender
import dev.dnpm.etl.processor.pseudonym.PseudonymizeService
import org.apache.commons.codec.binary.Base32
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Sinks
import java.util.*

@RestController
class MtbFileController(
    private val pseudonymizeService: PseudonymizeService,
    private val senders: List<MtbFileSender>,
    private val requestRepository: RequestRepository,
    private val objectMapper: ObjectMapper,
    private val statisticsUpdateProducer: Sinks.Many<Any>
) {

    private val logger = LoggerFactory.getLogger(MtbFileController::class.java)

    @PostMapping(path = ["/mtbfile"])
    fun mtbFile(@RequestBody mtbFile: MtbFile): ResponseEntity<Void> {
        val pid = mtbFile.patient.id
        val pseudonymized = pseudonymizeService.pseudonymize(mtbFile)

        val lastRequestForPatient =
            requestRepository.findAllByPatientIdOrderByProcessedAtDesc(pseudonymized.patient.id)
                .firstOrNull { it.status == RequestStatus.SUCCESS || it.status == RequestStatus.WARNING }

        if (null != lastRequestForPatient && lastRequestForPatient.fingerprint == fingerprint(mtbFile)) {
            requestRepository.save(
                Request(
                    patientId = pseudonymized.patient.id,
                    pid = pid,
                    fingerprint = fingerprint(mtbFile),
                    status = RequestStatus.DUPLICATION,
                    report = Report("Duplikat erkannt - keine Daten weitergeleitet")
                )
            )
            statisticsUpdateProducer.emitNext("", Sinks.EmitFailureHandler.FAIL_FAST)
            return ResponseEntity.noContent().build()
        }

        val request = MtbFileSender.MtbFileRequest(UUID.randomUUID().toString(), pseudonymized)

        val responses = senders.map {
            val responseStatus = it.send(request)
            if (responseStatus.status == MtbFileSender.ResponseStatus.SUCCESS || responseStatus.status == MtbFileSender.ResponseStatus.WARNING) {
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

        val requestStatus = if (responses.map { it.status }.contains(MtbFileSender.ResponseStatus.ERROR)) {
            RequestStatus.ERROR
        } else if (responses.map { it.status }.contains(MtbFileSender.ResponseStatus.WARNING)) {
            RequestStatus.WARNING
        } else if (responses.map { it.status }.contains(MtbFileSender.ResponseStatus.SUCCESS)) {
            RequestStatus.SUCCESS
        } else {
            RequestStatus.UNKNOWN
        }

        requestRepository.save(
            Request(
                uuid = request.requestId,
                patientId = request.mtbFile.patient.id,
                pid = pid,
                fingerprint = fingerprint(request.mtbFile),
                status = requestStatus,
                report = when (requestStatus) {
                    RequestStatus.ERROR -> Report("Fehler bei der Datenübertragung oder Inhalt nicht verarbeitbar")
                    RequestStatus.WARNING -> Report("Warnungen über mangelhafte Daten",
                        responses.joinToString("\n") { it.reason })

                    RequestStatus.UNKNOWN -> Report("Keine Informationen")
                    else -> null
                }
            )
        )

        statisticsUpdateProducer.emitNext("", Sinks.EmitFailureHandler.FAIL_FAST)

        return if (requestStatus == RequestStatus.ERROR) {
            ResponseEntity.unprocessableEntity().build()
        } else {
            ResponseEntity.noContent().build()
        }
    }

    @DeleteMapping(path = ["/mtbfile/{patientId}"])
    fun deleteData(@PathVariable patientId: String): ResponseEntity<Void> {
        val requestId = UUID.randomUUID().toString()

        try {
            val patientPseudonym = pseudonymizeService.patientPseudonym(patientId)

            val responses = senders.map {
                it.send(MtbFileSender.DeleteRequest(requestId, patientPseudonym))
            }

            val requestStatus = if (responses.map { it.status }.contains(MtbFileSender.ResponseStatus.ERROR)) {
                RequestStatus.DELETE_ERROR
            } else if (responses.map { it.status }.contains(MtbFileSender.ResponseStatus.SUCCESS)) {
                RequestStatus.DELETE_SUCCESS
            } else {
                RequestStatus.UNKNOWN
            }

            requestRepository.save(
                Request(
                    uuid = requestId,
                    patientId = patientPseudonym,
                    pid = patientId,
                    fingerprint = fingerprint(patientPseudonym),
                    status = requestStatus,
                    report = when (requestStatus) {
                        RequestStatus.DELETE_ERROR -> Report("Fehler bei der Datenübertragung oder Inhalt nicht verarbeitbar")
                        RequestStatus.UNKNOWN -> Report("Keine Informationen")
                        else -> null
                    }
                )
            )

            return ResponseEntity.unprocessableEntity().build()
        } catch (e: Exception) {
            requestRepository.save(
                Request(
                    uuid = requestId,
                    patientId = "???",
                    pid = patientId,
                    fingerprint = "",
                    status = RequestStatus.DELETE_ERROR,
                    report = Report("Fehler bei der Pseudonymisierung")
                )
            )

            return ResponseEntity.noContent().build()
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