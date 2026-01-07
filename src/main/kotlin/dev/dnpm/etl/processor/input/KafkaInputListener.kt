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
import dev.dnpm.etl.processor.CustomMediaType
import dev.dnpm.etl.processor.PatientId
import dev.dnpm.etl.processor.RequestId
import dev.dnpm.etl.processor.consent.ConsentEvaluator
import dev.dnpm.etl.processor.consent.TtpConsentStatus
import dev.dnpm.etl.processor.services.RequestProcessor
import dev.pcvolkmer.mv64e.mtb.Mtb
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.kafka.listener.MessageListener
import java.nio.charset.Charset

class KafkaInputListener(
    private val requestProcessor: RequestProcessor,
    private val consentEvaluator: ConsentEvaluator,
    private val objectMapper: ObjectMapper,
) : MessageListener<String, String> {
    private val logger = LoggerFactory.getLogger(KafkaInputListener::class.java)

    override fun onMessage(record: ConsumerRecord<String, String>) {
        when (guessMimeType(record)) {
            MediaType.APPLICATION_JSON_VALUE -> handleDnpmV2Message(record)
            CustomMediaType.APPLICATION_VND_DNPM_V2_MTB_JSON_VALUE -> handleDnpmV2Message(record)
            else -> {
                // ignore other messages
            }
        }
    }

    private fun guessMimeType(record: ConsumerRecord<String, String>): String? {
        if (record
                .headers()
                .headers("contentType")
                .toList()
                .isEmpty()
        ) {
            // Fallback if no contentType set (old behavior)
            return MediaType.APPLICATION_JSON_VALUE
        }

        return record
            .headers()
            .headers("contentType")
            ?.firstOrNull()
            ?.value()
            ?.toString(Charset.forName("UTF-8"))
    }

    private fun handleDnpmV2Message(record: ConsumerRecord<String, String>) {
        val mtbFile = objectMapper.readValue(record.value(), Mtb::class.java)
        val patientId = PatientId(mtbFile.patient.id)
        val firstRequestIdHeader = record.headers().headers("requestId")?.firstOrNull()
        val requestId =
            if (null != firstRequestIdHeader) {
                RequestId(String(firstRequestIdHeader.value()))
            } else {
                RequestId("")
            }
        val firstRequestMethodHeader = record.headers().headers("requestMethod")?.firstOrNull()
        val requestMethod =
            if (null != firstRequestMethodHeader) {
                String(firstRequestMethodHeader.value())
            } else {
                ""
            }

        if (requestMethod == "DELETE") {
            logger.debug("Accepted MTB File and process deletion")
            if (requestId.isBlank()) {
                requestProcessor.processDeletion(patientId, TtpConsentStatus.UNKNOWN_CHECK_FILE)
            } else {
                requestProcessor.processDeletion(patientId, requestId, TtpConsentStatus.UNKNOWN_CHECK_FILE)
            }
        } else {
            logger.debug("Accepted MTB File for processing")
            if (requestId.isBlank()) {
                requestProcessor.processMtbFile(mtbFile)
            } else {
                requestProcessor.processMtbFile(mtbFile, requestId)
            }
        }
    }
}
