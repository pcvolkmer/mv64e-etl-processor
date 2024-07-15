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

package dev.dnpm.etl.processor.input

import com.fasterxml.jackson.databind.ObjectMapper
import de.ukw.ccc.bwhc.dto.Consent
import de.ukw.ccc.bwhc.dto.MtbFile
import de.ukw.ccc.bwhc.dto.Patient
import dev.dnpm.etl.processor.anyValueClass
import dev.dnpm.etl.processor.services.RequestProcessor
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.*

@ExtendWith(MockitoExtension::class)
class KafkaInputListenerTest {

    private lateinit var requestProcessor: RequestProcessor
    private lateinit var objectMapper: ObjectMapper
    private lateinit var kafkaInputListener: KafkaInputListener

    @BeforeEach
    fun setup(
        @Mock requestProcessor: RequestProcessor
    ) {
        this.requestProcessor = requestProcessor
        this.objectMapper = ObjectMapper()

        this.kafkaInputListener = KafkaInputListener(requestProcessor, objectMapper)
    }

    @Test
    fun shouldProcessMtbFileRequest() {
        val mtbFile = MtbFile.builder()
            .withPatient(Patient.builder().withId("DUMMY_12345678").build())
            .withConsent(Consent.builder().withStatus(Consent.Status.ACTIVE).build())
            .build()

        kafkaInputListener.onMessage(ConsumerRecord("testtopic", 0, 0, "", this.objectMapper.writeValueAsString(mtbFile)))

        verify(requestProcessor, times(1)).processMtbFile(any())
    }

    @Test
    fun shouldProcessDeleteRequest() {
        val mtbFile = MtbFile.builder()
            .withPatient(Patient.builder().withId("DUMMY_12345678").build())
            .withConsent(Consent.builder().withStatus(Consent.Status.REJECTED).build())
            .build()

        kafkaInputListener.onMessage(ConsumerRecord("testtopic", 0, 0, "", this.objectMapper.writeValueAsString(mtbFile)))

        verify(requestProcessor, times(1)).processDeletion(anyValueClass())
    }

    @Test
    fun shouldProcessMtbFileRequestWithExistingRequestId() {
        val mtbFile = MtbFile.builder()
            .withPatient(Patient.builder().withId("DUMMY_12345678").build())
            .withConsent(Consent.builder().withStatus(Consent.Status.ACTIVE).build())
            .build()

        val headers = RecordHeaders(listOf(RecordHeader("requestId", UUID.randomUUID().toString().toByteArray())))
        kafkaInputListener.onMessage(
            ConsumerRecord("testtopic", 0, 0, -1L, TimestampType.NO_TIMESTAMP_TYPE, -1, -1, "", this.objectMapper.writeValueAsString(mtbFile), headers, Optional.empty())
        )

        verify(requestProcessor, times(1)).processMtbFile(any(), anyValueClass())
    }

    @Test
    fun shouldProcessDeleteRequestWithExistingRequestId() {
        val mtbFile = MtbFile.builder()
            .withPatient(Patient.builder().withId("DUMMY_12345678").build())
            .withConsent(Consent.builder().withStatus(Consent.Status.REJECTED).build())
            .build()

        val headers = RecordHeaders(listOf(RecordHeader("requestId", UUID.randomUUID().toString().toByteArray())))
        kafkaInputListener.onMessage(
            ConsumerRecord("testtopic", 0, 0, -1L, TimestampType.NO_TIMESTAMP_TYPE, -1, -1, "", this.objectMapper.writeValueAsString(mtbFile), headers, Optional.empty())
        )
        verify(requestProcessor, times(1)).processDeletion(anyValueClass(), anyValueClass())
    }

}