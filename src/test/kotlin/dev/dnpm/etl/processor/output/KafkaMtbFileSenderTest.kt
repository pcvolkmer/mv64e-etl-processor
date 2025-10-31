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
import dev.dnpm.etl.processor.PatientPseudonym
import dev.dnpm.etl.processor.RequestId
import dev.dnpm.etl.processor.config.KafkaProperties
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.pcvolkmer.mv64e.mtb.*
import org.apache.kafka.clients.producer.ProducerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.retry.policy.SimpleRetryPolicy
import org.springframework.retry.support.RetryTemplateBuilder
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.ExecutionException

@ExtendWith(MockitoExtension::class)
class KafkaMtbFileSenderTest {

    @Nested
    inner class BwhcV1Record {

        private lateinit var kafkaTemplate: KafkaTemplate<String, String>

        private lateinit var kafkaMtbFileSender: KafkaMtbFileSender

        private lateinit var objectMapper: ObjectMapper

        @BeforeEach
        fun setup(
            @Mock kafkaTemplate: KafkaTemplate<String, String>
        ) {
            val kafkaProperties = KafkaProperties("testtopic")
            val retryTemplate = RetryTemplateBuilder().customPolicy(SimpleRetryPolicy(1)).build()

            this.objectMapper = ObjectMapper()
            this.kafkaTemplate = kafkaTemplate

            this.kafkaMtbFileSender = KafkaMtbFileSender(kafkaTemplate, kafkaProperties, retryTemplate, objectMapper)
        }

        @ParameterizedTest
        @MethodSource("dev.dnpm.etl.processor.output.KafkaMtbFileSenderTest#requestWithResponseSource")
        fun shouldSendDeleteRequestAndReturnExpectedState(testData: TestData) {
            doAnswer {
                if (null != testData.exception) {
                    throw testData.exception
                }
                completedFuture(SendResult<String, String>(null, null))
            }.whenever(kafkaTemplate).send(any<ProducerRecord<String, String>>())

            val response = kafkaMtbFileSender.send(DeleteRequest(TEST_REQUEST_ID, TEST_PATIENT_PSEUDONYM))
            assertThat(response.status).isEqualTo(testData.requestStatus)
        }

        @ParameterizedTest
        @MethodSource("dev.dnpm.etl.processor.output.KafkaMtbFileSenderTest#requestWithResponseSource")
        fun shouldRetryOnDeleteKafkaSendError(testData: TestData) {
            val kafkaProperties = KafkaProperties("testtopic")
            val retryTemplate = RetryTemplateBuilder().customPolicy(SimpleRetryPolicy(3)).build()
            this.kafkaMtbFileSender = KafkaMtbFileSender(this.kafkaTemplate, kafkaProperties, retryTemplate, this.objectMapper)

            doAnswer {
                if (null != testData.exception) {
                    throw testData.exception
                }
                completedFuture(SendResult<String, String>(null, null))
            }.whenever(kafkaTemplate).send(any<ProducerRecord<String, String>>())

            kafkaMtbFileSender.send(DeleteRequest(TEST_REQUEST_ID, TEST_PATIENT_PSEUDONYM))

            val expectedCount = when (testData.exception) {
                // OK - No Retry
                null -> times(1)
                // Request failed - Retry max 3 times
                else -> times(3)
            }

            verify(kafkaTemplate, expectedCount).send(any<ProducerRecord<String, String>>())
        }

    }

    @Nested
    inner class DnpmV2Record {

        private lateinit var kafkaTemplate: KafkaTemplate<String, String>

        private lateinit var kafkaMtbFileSender: KafkaMtbFileSender

        private lateinit var objectMapper: ObjectMapper

        @BeforeEach
        fun setup(
            @Mock kafkaTemplate: KafkaTemplate<String, String>
        ) {
            val kafkaProperties = KafkaProperties("testtopic")
            val retryTemplate = RetryTemplateBuilder().customPolicy(SimpleRetryPolicy(1)).build()

            this.objectMapper = ObjectMapper()
            this.kafkaTemplate = kafkaTemplate

            this.kafkaMtbFileSender = KafkaMtbFileSender(kafkaTemplate, kafkaProperties, retryTemplate, objectMapper)
        }

        @ParameterizedTest
        @MethodSource("dev.dnpm.etl.processor.output.KafkaMtbFileSenderTest#requestWithResponseSource")
        fun shouldSendMtbFileRequestAndReturnExpectedState(testData: TestData) {
            doAnswer {
                if (null != testData.exception) {
                    throw testData.exception
                }
                completedFuture(SendResult<String, String>(null, null))
            }.whenever(kafkaTemplate).send(any<ProducerRecord<String, String>>())

            val response = kafkaMtbFileSender.send(DnpmV2MtbFileRequest(TEST_REQUEST_ID, dnpmV2MtbFile()))
            assertThat(response.status).isEqualTo(testData.requestStatus)
        }

        @Test
        fun shouldSendMtbFileRequestWithCorrectKeyAndHeaderAndBody() {
            doAnswer {
                completedFuture(SendResult<String, String>(null, null))
            }.whenever(kafkaTemplate).send(any<ProducerRecord<String, String>>())

            kafkaMtbFileSender.send(DnpmV2MtbFileRequest(TEST_REQUEST_ID, dnpmV2MtbFile()))

            val captor = argumentCaptor<ProducerRecord<String, String>>()
            verify(kafkaTemplate, times(1)).send(captor.capture())
            assertThat(captor.firstValue.key()).isNotNull
            assertThat(captor.firstValue.key()).isEqualTo("{\"pid\": \"PID\"}")
            assertThat(captor.firstValue.headers().headers("contentType")).isNotNull
            assertThat(captor.firstValue.headers().headers("contentType")?.firstOrNull()?.value()).isEqualTo(CustomMediaType.APPLICATION_VND_DNPM_V2_MTB_JSON_VALUE.toByteArray())
            assertThat(captor.firstValue.headers().headers("requestId")).isNotNull
            assertThat(captor.firstValue.headers().headers("requestId")?.firstOrNull()?.value()).isEqualTo(TEST_REQUEST_ID.value.toByteArray())
            assertThat(captor.firstValue.value()).isNotNull
            assertThat(captor.firstValue.value()).isEqualTo(objectMapper.writeValueAsString(dnmpV2kafkaRecordData(TEST_REQUEST_ID)))
        }

        @ParameterizedTest
        @MethodSource("dev.dnpm.etl.processor.output.KafkaMtbFileSenderTest#requestWithResponseSource")
        fun shouldRetryOnMtbFileKafkaSendError(testData: TestData) {
            val kafkaProperties = KafkaProperties("testtopic")
            val retryTemplate = RetryTemplateBuilder().customPolicy(SimpleRetryPolicy(3)).build()
            this.kafkaMtbFileSender = KafkaMtbFileSender(this.kafkaTemplate, kafkaProperties, retryTemplate, this.objectMapper)

            doAnswer {
                if (null != testData.exception) {
                    throw testData.exception
                }
                completedFuture(SendResult<String, String>(null, null))
            }.whenever(kafkaTemplate).send(any<ProducerRecord<String, String>>())

            kafkaMtbFileSender.send(DnpmV2MtbFileRequest(TEST_REQUEST_ID, dnpmV2MtbFile()))

            val expectedCount = when (testData.exception) {
                // OK - No Retry
                null -> times(1)
                // Request failed - Retry max 3 times
                else -> times(3)
            }

            verify(kafkaTemplate, expectedCount).send(any<ProducerRecord<String, String>>())
        }

    }

    companion object {
        val TEST_REQUEST_ID = RequestId("TestId")
        val TEST_PATIENT_PSEUDONYM = PatientPseudonym("PID")

        fun dnpmV2MtbFile(): Mtb {
            return Mtb().apply {
                this.patient = dev.pcvolkmer.mv64e.mtb.Patient().apply {
                    this.id = "PID"
                    this.birthDate = Date.from(Instant.now())
                    this.gender = GenderCoding().apply {
                        this.code = GenderCodingCode.MALE
                    }
                }
                this.episodesOfCare = listOf(
                    MtbEpisodeOfCare().apply {
                        this.id = "1"
                        this.patient = Reference().apply {
                            this.id = "PID"
                        }
                        this.period = PeriodDate().apply {
                            this.start = Date.from(Instant.now())
                        }
                    }
                )
            }
        }

        fun dnmpV2kafkaRecordData(requestId: RequestId): Mtb {
            return DnpmV2MtbFileRequest(requestId, dnpmV2MtbFile()).content
        }

        data class TestData(val requestStatus: RequestStatus, val exception: Throwable? = null)

        @JvmStatic
        fun requestWithResponseSource(): Set<TestData> {
            return setOf(
                TestData(RequestStatus.UNKNOWN),
                TestData(RequestStatus.ERROR, InterruptedException("Test interrupted")),
                TestData(RequestStatus.ERROR, ExecutionException(RuntimeException("Test execution aborted")))
            )
        }
    }

}
