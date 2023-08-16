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

package dev.dnpm.etl.processor.output

import com.fasterxml.jackson.databind.ObjectMapper
import de.ukw.ccc.bwhc.dto.*
import dev.dnpm.etl.processor.config.KafkaTargetProperties
import dev.dnpm.etl.processor.monitoring.RequestStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.ExecutionException

@ExtendWith(MockitoExtension::class)
class KafkaMtbFileSenderTest {

    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    private lateinit var kafkaMtbFileSender: KafkaMtbFileSender

    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setup(
        @Mock kafkaTemplate: KafkaTemplate<String, String>
    ) {
        val kafkaTargetProperties = KafkaTargetProperties("testtopic")
        this.objectMapper = ObjectMapper()
        this.kafkaTemplate = kafkaTemplate

        this.kafkaMtbFileSender = KafkaMtbFileSender(kafkaTemplate, kafkaTargetProperties, objectMapper)
    }

    @ParameterizedTest
    @MethodSource("requestWithResponseSource")
    fun shouldSendMtbFileRequestAndReturnExpectedState(testData: TestData) {
        doAnswer {
            if (null != testData.exception) {
                throw testData.exception
            }
            completedFuture(SendResult<String, String>(null, null))
        }.whenever(kafkaTemplate).send(anyString(), anyString(), anyString())

        val response = kafkaMtbFileSender.send(MtbFileSender.MtbFileRequest("TestID", mtbFile(Consent.Status.ACTIVE)))
        assertThat(response.status).isEqualTo(testData.requestStatus)
    }

    @ParameterizedTest
    @MethodSource("requestWithResponseSource")
    fun shouldSendDeleteRequestAndReturnExpectedState(testData: TestData) {
        doAnswer {
            if (null != testData.exception) {
                throw testData.exception
            }
            completedFuture(SendResult<String, String>(null, null))
        }.whenever(kafkaTemplate).send(anyString(), anyString(), anyString())

        val response = kafkaMtbFileSender.send(MtbFileSender.DeleteRequest("TestID", "PID"))
        assertThat(response.status).isEqualTo(testData.requestStatus)
    }

    @Test
    fun shouldSendMtbFileRequestWithCorrectKeyAndBody() {
        doAnswer {
            completedFuture(SendResult<String, String>(null, null))
        }.whenever(kafkaTemplate).send(anyString(), anyString(), anyString())

        kafkaMtbFileSender.send(MtbFileSender.MtbFileRequest("TestID", mtbFile(Consent.Status.ACTIVE)))

        val captor = argumentCaptor<String>()
        verify(kafkaTemplate, times(1)).send(anyString(), captor.capture(), captor.capture())
        assertThat(captor.firstValue).isNotNull
        assertThat(captor.firstValue).isEqualTo("{\"pid\": \"PID\", \"eid\": \"1\"}")
        assertThat(captor.secondValue).isNotNull
        assertThat(captor.secondValue).isEqualTo(objectMapper.writeValueAsString(kafkaRecordData("TestID", Consent.Status.ACTIVE)))
    }

    @Test
    fun shouldSendDeleteRequestWithCorrectKeyAndBody() {
        doAnswer {
            completedFuture(SendResult<String, String>(null, null))
        }.whenever(kafkaTemplate).send(anyString(), anyString(), anyString())

        kafkaMtbFileSender.send(MtbFileSender.DeleteRequest("TestID", "PID"))

        val captor = argumentCaptor<String>()
        verify(kafkaTemplate, times(1)).send(anyString(), captor.capture(), captor.capture())
        assertThat(captor.firstValue).isNotNull
        assertThat(captor.firstValue).isEqualTo("{\"pid\": \"PID\"}")
        assertThat(captor.secondValue).isNotNull
        assertThat(captor.secondValue).isEqualTo(objectMapper.writeValueAsString(kafkaRecordData("TestID", Consent.Status.REJECTED)))
    }

    companion object {
        fun mtbFile(consentStatus: Consent.Status): MtbFile {
            return if (consentStatus == Consent.Status.ACTIVE) {
                MtbFile.builder()
                    .withPatient(
                        Patient.builder()
                            .withId("PID")
                            .withBirthDate("2000-08-08")
                            .withGender(Patient.Gender.MALE)
                            .build()
                    )
                    .withConsent(
                        Consent.builder()
                            .withId("1")
                            .withStatus(consentStatus)
                            .withPatient("PID")
                            .build()
                    )
                    .withEpisode(
                        Episode.builder()
                            .withId("1")
                            .withPatient("PID")
                            .withPeriod(PeriodStart("2023-08-08"))
                            .build()
                    )
            } else {
                MtbFile.builder()
                    .withConsent(
                        Consent.builder()
                            .withStatus(consentStatus)
                            .withPatient("PID")
                            .build()
                    )
            }.build()
        }

        fun kafkaRecordData(requestId: String, consentStatus: Consent.Status): KafkaMtbFileSender.Data {
            return KafkaMtbFileSender.Data(requestId, mtbFile(consentStatus))
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