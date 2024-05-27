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
import dev.dnpm.etl.processor.RequestId
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.dnpm.etl.processor.output.asRequestStatus
import dev.dnpm.etl.processor.services.ResponseEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.kafka.listener.MessageListener
import java.time.Instant
import java.util.*

class KafkaResponseProcessor(
    private val eventPublisher: ApplicationEventPublisher,
    private val objectMapper: ObjectMapper
) : MessageListener<String, String> {

    private val logger = LoggerFactory.getLogger(KafkaResponseProcessor::class.java)

    override fun onMessage(data: ConsumerRecord<String, String>) {
        try {
            Optional.of(objectMapper.readValue(data.value(), ResponseBody::class.java))
        } catch (e: Exception) {
            logger.error("Cannot process Kafka response", e)
            Optional.empty()
        }.ifPresentOrElse({ responseBody ->
            val event = ResponseEvent(
                RequestId(responseBody.requestId),
                Instant.ofEpochMilli(data.timestamp()),
                responseBody.statusCode.asRequestStatus(),
                when (responseBody.statusCode.asRequestStatus()) {
                    RequestStatus.SUCCESS -> {
                        Optional.empty()
                    }

                    RequestStatus.WARNING, RequestStatus.ERROR -> {
                        Optional.of(objectMapper.writeValueAsString(responseBody.statusBody))
                    }

                    else -> {
                        logger.error("Kafka response: Unknown response code '{}'!", responseBody.statusCode)
                        Optional.empty()
                    }
                }
            )
            eventPublisher.publishEvent(event)
        }, {
            logger.error("No requestId in Kafka response")
        })
    }

    data class ResponseBody(
        @JsonProperty("request_id") @JsonAlias("requestId") val requestId: String,
        @JsonProperty("status_code") @JsonAlias("statusCode") val statusCode: Int,
        @JsonProperty("status_body") @JsonAlias("statusBody") val statusBody: Map<String, Any>
    )

}