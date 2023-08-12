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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import dev.dnpm.etl.processor.services.ResponseEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus

@ExtendWith(MockitoExtension::class)
class KafkaResponseProcessorTest {

    private lateinit var eventPublisher: ApplicationEventPublisher
    private lateinit var objectMapper: ObjectMapper

    private lateinit var kafkaResponseProcessor: KafkaResponseProcessor

    private fun createKafkaRecord(
        requestId: String? = null,
        statusCode: Int = 200,
        statusBody: Map<String, Any>? = mapOf()
    ): ConsumerRecord<String, String> {
        return ConsumerRecord<String, String>(
            "test-topic",
            0,
            0,
            if (requestId == null) {
                null
            } else {
                this.objectMapper.writeValueAsString(KafkaResponseProcessor.ResponseKey(requestId))
            },
            if (statusBody == null) {
                ""
            } else {
                this.objectMapper.writeValueAsString(KafkaResponseProcessor.ResponseBody(statusCode, statusBody))
            }
        )
    }

    @BeforeEach
    fun setup(
        @Mock eventPublisher: ApplicationEventPublisher
    ) {
        this.eventPublisher = eventPublisher
        this.objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

        this.kafkaResponseProcessor = KafkaResponseProcessor(eventPublisher, objectMapper)
    }

    @Test
    fun shouldNotProcessRecordsWithoutValidKey() {
        this.kafkaResponseProcessor.onMessage(createKafkaRecord(null, 200))

        verify(eventPublisher, never()).publishEvent(any())
    }

    @Test
    fun shouldNotProcessRecordsWithoutValidBody() {
        this.kafkaResponseProcessor.onMessage(createKafkaRecord(requestId = "TestID1234", statusBody = null))

        verify(eventPublisher, never()).publishEvent(any())
    }

    @ParameterizedTest
    @MethodSource("statusCodeSource")
    fun shouldProcessValidRecordsWithStatusCode(statusCode: Int) {
        this.kafkaResponseProcessor.onMessage(createKafkaRecord("TestID1234", statusCode))
        verify(eventPublisher, times(1)).publishEvent(any<ResponseEvent>())
    }

    companion object {

        @JvmStatic
        fun statusCodeSource(): Set<Int> {
            return setOf(
                HttpStatus.OK,
                HttpStatus.CREATED,
                HttpStatus.BAD_REQUEST,
                HttpStatus.NOT_FOUND,
                HttpStatus.UNPROCESSABLE_ENTITY,
                HttpStatus.INTERNAL_SERVER_ERROR
            )
                .map { it.value() }
                .toSet()
        }

    }

}