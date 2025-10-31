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

package dev.dnpm.etl.processor.output

import com.fasterxml.jackson.databind.ObjectMapper
import dev.dnpm.etl.processor.CustomMediaType
import dev.dnpm.etl.processor.config.KafkaProperties
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.pcvolkmer.mv64e.mtb.Mtb
import dev.pcvolkmer.mv64e.mtb.MvhMetadata
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.retry.support.RetryTemplate

class KafkaMtbFileSender(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val kafkaProperties: KafkaProperties,
    private val retryTemplate: RetryTemplate,
    private val objectMapper: ObjectMapper
) : MtbFileSender {

    private val logger = LoggerFactory.getLogger(KafkaMtbFileSender::class.java)

    override fun <T> send(request: MtbFileRequest<T>): MtbFileSender.Response {
        return try {
            return retryTemplate.execute<MtbFileSender.Response, Exception> {
                val record =
                    ProducerRecord(
                        kafkaProperties.outputTopic,
                        key(request),
                        objectMapper.writeValueAsString(request.content),
                    )
                record.headers().add("requestId", request.requestId.value.toByteArray())
                when (request) {
                    is DnpmV2MtbFileRequest -> record.headers()
                        .add(
                            "contentType",
                            CustomMediaType.APPLICATION_VND_DNPM_V2_MTB_JSON_VALUE.toByteArray()
                        )
                }

                val result = kafkaTemplate.send(record)
                if (result.get() != null) {
                    logger.debug("Sent file via KafkaMtbFileSender")
                    MtbFileSender.Response(RequestStatus.UNKNOWN)
                } else {
                    MtbFileSender.Response(RequestStatus.ERROR)
                }
            }
        } catch (e: Exception) {
            logger.error("An error occurred sending to kafka", e)
            MtbFileSender.Response(RequestStatus.ERROR)
        }
    }

    override fun send(request: DeleteRequest): MtbFileSender.Response {
        val dummyMtbFile = Mtb.builder()
            .metadata(MvhMetadata())
            .build()

        return try {
            return retryTemplate.execute<MtbFileSender.Response, Exception> {
                val record =
                    ProducerRecord(
                        kafkaProperties.outputTopic,
                        key(request),
                        objectMapper.writeValueAsString(
                            DnpmV2MtbFileRequest(
                                request.requestId,
                                dummyMtbFile
                            )
                        )
                    )
                record.headers().add("requestId", request.requestId.value.toByteArray())
                val result = kafkaTemplate.send(record)
                if (result.get() != null) {
                    logger.debug("Sent deletion request via KafkaMtbFileSender")
                    MtbFileSender.Response(RequestStatus.UNKNOWN)
                } else {
                    MtbFileSender.Response(RequestStatus.ERROR)
                }
            }
        } catch (e: Exception) {
            logger.error("An error occurred sending to kafka", e)
            MtbFileSender.Response(RequestStatus.ERROR)
        }
    }

    override fun endpoint(): String {
        return "${this.kafkaProperties.servers} (${this.kafkaProperties.outputTopic}/${this.kafkaProperties.outputResponseTopic})"
    }

    private fun key(request: MtbRequest): String {
        return when (request) {
            is DnpmV2MtbFileRequest -> "{\"pid\": \"${request.content.patient.id}\"}"
            is DeleteRequest -> "{\"pid\": \"${request.patientId.value}\"}"
            else -> throw IllegalArgumentException("Unsupported request type: ${request::class.simpleName}")
        }
    }
}
