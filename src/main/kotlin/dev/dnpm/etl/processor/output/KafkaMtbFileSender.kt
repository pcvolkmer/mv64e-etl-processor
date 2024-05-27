/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2024  Comprehensive Cancer Center Mainfranken, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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
import de.ukw.ccc.bwhc.dto.Consent
import de.ukw.ccc.bwhc.dto.MtbFile
import dev.dnpm.etl.processor.RequestId
import dev.dnpm.etl.processor.config.KafkaProperties
import dev.dnpm.etl.processor.monitoring.RequestStatus
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

    override fun send(request: MtbFileSender.MtbFileRequest): MtbFileSender.Response {
        return try {
            return retryTemplate.execute<MtbFileSender.Response, Exception> {
                val result = kafkaTemplate.send(
                    kafkaProperties.topic,
                    key(request),
                    objectMapper.writeValueAsString(Data(request.requestId, request.mtbFile))
                )
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

    override fun send(request: MtbFileSender.DeleteRequest): MtbFileSender.Response {
        val dummyMtbFile = MtbFile.builder()
            .withConsent(
                Consent.builder()
                    .withPatient(request.patientId)
                    .withStatus(Consent.Status.REJECTED)
                    .build()
            )
            .build()

        return try {
            return retryTemplate.execute<MtbFileSender.Response, Exception> {
                val result = kafkaTemplate.send(
                    kafkaProperties.topic,
                    key(request),
                    objectMapper.writeValueAsString(Data(request.requestId, dummyMtbFile))
                )

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
        return "${this.kafkaProperties.servers} (${this.kafkaProperties.topic}/${this.kafkaProperties.responseTopic})"
    }

    private fun key(request: MtbFileSender.MtbFileRequest): String {
        return "{\"pid\": \"${request.mtbFile.patient.id}\"}"
    }

    private fun key(request: MtbFileSender.DeleteRequest): String {
        return "{\"pid\": \"${request.patientId}\"}"
    }

    data class Data(val requestId: RequestId, val content: MtbFile)
}