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

package dev.dnpm.etl.processor.input

import com.fasterxml.jackson.databind.ObjectMapper
import de.ukw.ccc.bwhc.dto.Consent
import de.ukw.ccc.bwhc.dto.MtbFile
import dev.dnpm.etl.processor.CustomMediaType
import dev.dnpm.etl.processor.PatientId
import dev.dnpm.etl.processor.RequestId
import dev.dnpm.etl.processor.consent.TtpConsentStatus
import dev.dnpm.etl.processor.services.RequestProcessor
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.kafka.listener.MessageListener

class KafkaInputListener(
    private val requestProcessor: RequestProcessor,
    private val objectMapper: ObjectMapper
) : MessageListener<String, String> {
    private val logger = LoggerFactory.getLogger(KafkaInputListener::class.java)

    override fun onMessage(record: ConsumerRecord<String, String>) {
        when (guessMimeType(record)) {
            MediaType.APPLICATION_JSON_VALUE -> handleBwhcMessage(record)
            CustomMediaType.APPLICATION_VND_DNPM_V2_MTB_JSON_VALUE -> handleDnpmV2Message(record)
            else -> {
                /* ignore other messages */
            }
        }
    }

    private fun guessMimeType(record: ConsumerRecord<String, String>): String {
        if (record.headers().headers("contentType").toList().isEmpty()) {
            // Fallback if no contentType set (old behavior)
            return MediaType.APPLICATION_JSON_VALUE
        }

        return record.headers().headers("contentType")?.firstOrNull()?.value().contentToString()
    }

    private fun handleBwhcMessage(record: ConsumerRecord<String, String>) {
        val mtbFile = objectMapper.readValue(record.value(), MtbFile::class.java)
        val patientId = PatientId(mtbFile.patient.id)
        val firstRequestIdHeader = record.headers().headers("requestId")?.firstOrNull()
        val requestId = if (null != firstRequestIdHeader) {
            RequestId(String(firstRequestIdHeader.value()))
        } else {
            RequestId("")
        }

        if (mtbFile.consent.status == Consent.Status.ACTIVE) {
            logger.debug("Accepted MTB File for processing")
            if (requestId.isBlank()) {
                requestProcessor.processMtbFile(mtbFile)
            } else {
                requestProcessor.processMtbFile(mtbFile, requestId)
            }
        } else {
            logger.debug("Accepted MTB File and process deletion")
            if (requestId.isBlank()) {
                requestProcessor.processDeletion(patientId, TtpConsentStatus.UNKNOWN_CHECK_FILE)
            } else {
                requestProcessor.processDeletion(
                    patientId,
                    requestId,
                    TtpConsentStatus.UNKNOWN_CHECK_FILE
                )
            }
        }
    }

    private fun handleDnpmV2Message(record: ConsumerRecord<String, String>) {
        // Do not handle DNPM-V2 for now
        logger.warn("Ignoring MTB File in DNPM V2 format: Not implemented yet")
    }

}
