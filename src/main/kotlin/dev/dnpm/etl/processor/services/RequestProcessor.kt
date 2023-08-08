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
import dev.dnpm.etl.processor.monitoring.Report
import dev.dnpm.etl.processor.monitoring.Request
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.dnpm.etl.processor.monitoring.RequestType
import dev.dnpm.etl.processor.output.MtbFileSender
import dev.dnpm.etl.processor.pseudonym.PseudonymizeService
import org.apache.commons.codec.binary.Base32
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Sinks
import java.util.*

@Service
class RequestProcessor(
    private val pseudonymizeService: PseudonymizeService,
    private val senders: List<MtbFileSender>,
    private val requestService: RequestService,
    private val objectMapper: ObjectMapper,
    private val statisticsUpdateProducer: Sinks.Many<Any>
) {

    private val logger = LoggerFactory.getLogger(RequestProcessor::class.java)

    fun processMtbFile(mtbFile: MtbFile) {
        val pid = mtbFile.patient.id
        val pseudonymized = pseudonymizeService.pseudonymize(mtbFile)

        if (isDuplication(pseudonymized)) {
            requestService.save(
                Request(
                    patientId = pseudonymized.patient.id,
                    pid = pid,
                    fingerprint = fingerprint(mtbFile),
                    status = RequestStatus.DUPLICATION,
                    type = RequestType.MTB_FILE,
                    report = Report("Duplikat erkannt - keine Daten weitergeleitet")
                )
            )
            statisticsUpdateProducer.emitNext("", Sinks.EmitFailureHandler.FAIL_FAST)
            return
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

        requestService.save(
            Request(
                uuid = request.requestId,
                patientId = request.mtbFile.patient.id,
                pid = pid,
                fingerprint = fingerprint(request.mtbFile),
                status = requestStatus,
                type = RequestType.MTB_FILE,
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
    }

    private fun isDuplication(pseudonymizedMtbFile: MtbFile): Boolean {
        val lastMtbFileRequestForPatient =
            requestService.lastMtbFileRequestForPatientPseudonym(pseudonymizedMtbFile.patient.id)
        val isLastRequestDeletion = requestService.isLastRequestDeletion(pseudonymizedMtbFile.patient.id)

        return null != lastMtbFileRequestForPatient
                && !isLastRequestDeletion
                && lastMtbFileRequestForPatient.fingerprint == fingerprint(pseudonymizedMtbFile)
    }

    fun processDeletion(patientId: String) {
        val requestId = UUID.randomUUID().toString()

        try {
            val patientPseudonym = pseudonymizeService.patientPseudonym(patientId)

            val responses = senders.map {
                val responseStatus = it.send(MtbFileSender.DeleteRequest(requestId, patientPseudonym))
                when (responseStatus.status) {
                    MtbFileSender.ResponseStatus.SUCCESS -> {
                        logger.info(
                            "Sent delete for Patient '{}' using '{}'",
                            patientPseudonym,
                            it.javaClass.simpleName
                        )
                    }

                    MtbFileSender.ResponseStatus.ERROR -> {
                        logger.error(
                            "Error deleting data for Patient '{}' using '{}'",
                            patientPseudonym,
                            it.javaClass.simpleName
                        )
                    }

                    else -> {
                        logger.error(
                            "Unknown result on deleting data for Patient '{}' using '{}'",
                            patientPseudonym,
                            it.javaClass.simpleName
                        )
                    }
                }
                responseStatus
            }

            val overallRequestStatus = if (responses.map { it.status }.contains(MtbFileSender.ResponseStatus.ERROR)) {
                RequestStatus.ERROR
            } else if (responses.map { it.status }.contains(MtbFileSender.ResponseStatus.SUCCESS)) {
                RequestStatus.SUCCESS
            } else {
                RequestStatus.UNKNOWN
            }

            requestService.save(
                Request(
                    uuid = requestId,
                    patientId = patientPseudonym,
                    pid = patientId,
                    fingerprint = fingerprint(patientPseudonym),
                    status = overallRequestStatus,
                    type = RequestType.DELETE,
                    report = when (overallRequestStatus) {
                        RequestStatus.ERROR -> Report("Fehler bei der Datenübertragung oder Inhalt nicht verarbeitbar")
                        RequestStatus.UNKNOWN -> Report("Keine Informationen")
                        else -> null
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
        statisticsUpdateProducer.emitNext("", Sinks.EmitFailureHandler.FAIL_FAST)
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