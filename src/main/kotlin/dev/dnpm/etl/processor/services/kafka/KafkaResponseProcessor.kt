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

package dev.dnpm.etl.processor.services.kafka

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import dev.dnpm.etl.processor.monitoring.Report
import dev.dnpm.etl.processor.monitoring.RequestRepository
import dev.dnpm.etl.processor.monitoring.RequestStatus
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.listener.MessageListener
import java.time.Instant

class KafkaResponseProcessor(
    private val requestRepository: RequestRepository,
    private val objectMapper: ObjectMapper
) : MessageListener<String, String> {

    private val logger = LoggerFactory.getLogger(KafkaResponseProcessor::class.java)

    override fun onMessage(data: ConsumerRecord<String, String>) {
        try {
            val responseKey = objectMapper.readValue(data.key(), ResponseKey::class.java)
            requestRepository.findByUuidEquals(responseKey.requestId).ifPresent {
                val responseBody = objectMapper.readValue(data.value(), ResponseBody::class.java)
                when (responseBody.statusCode) {
                    200 -> {
                        it.status = RequestStatus.SUCCESS
                        it.processedAt = Instant.ofEpochMilli(data.timestamp())
                        requestRepository.save(it)
                    }

                    201 -> {
                        it.status = RequestStatus.WARNING
                        it.processedAt = Instant.ofEpochMilli(data.timestamp())
                        it.report = Report(
                            "Warnungen über mangelhafte Daten",
                            objectMapper.writeValueAsString(responseBody.statusBody)
                        )
                        requestRepository.save(it)
                    }

                    400, 422 -> {
                        it.status = RequestStatus.ERROR
                        it.processedAt = Instant.ofEpochMilli(data.timestamp())
                        it.report = Report(
                            "Fehler bei der Datenübertragung oder Inhalt nicht verarbeitbar",
                            objectMapper.writeValueAsString(responseBody.statusBody)
                        )
                        requestRepository.save(it)
                    }

                    else -> {
                        logger.error("Cannot process Kafka response: Unknown response code!")
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Cannot process Kafka response", e)
        }
    }

    data class ResponseKey(val requestId: String)

    data class ResponseBody(
        @JsonProperty("status_code") @JsonAlias("status code") val statusCode: Int,
        @JsonProperty("status_body") val statusBody: Map<String, Any>
    )
}