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
import dev.dnpm.etl.processor.consent.ConsentEvaluation
import dev.dnpm.etl.processor.consent.ConsentEvaluator
import dev.dnpm.etl.processor.consent.TtpConsentStatus
import dev.dnpm.etl.processor.services.RequestProcessor
import dev.pcvolkmer.mv64e.mtb.*
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.util.*

@ExtendWith(MockitoExtension::class)
class KafkaInputListenerTest {

    private lateinit var requestProcessor: RequestProcessor
    private lateinit var consentEvaluator: ConsentEvaluator
    private lateinit var objectMapper: ObjectMapper

    private lateinit var kafkaInputListener: KafkaInputListener

    @BeforeEach
    fun setup(
        @Mock requestProcessor: RequestProcessor,
        @Mock consentEvaluator: ConsentEvaluator,
    ) {
        this.requestProcessor = requestProcessor
        this.consentEvaluator = consentEvaluator
        this.objectMapper = ObjectMapper()

        this.kafkaInputListener = KafkaInputListener(requestProcessor, consentEvaluator, objectMapper)
    }

    @Test
    fun shouldProcessMtbFileRequest() {
        whenever(consentEvaluator.check(any())).thenReturn(
            ConsentEvaluation(
                TtpConsentStatus.BROAD_CONSENT_GIVEN,
                true
            )
        )

        val mtbFile = Mtb.builder()
            .patient(Patient.builder().id("DUMMY_12345678").build())
            .metadata(
                MvhMetadata
                    .builder()
                    .modelProjectConsent(
                        ModelProjectConsent
                            .builder()
                            .provisions(
                                listOf(
                                    Provision.builder().type(ConsentProvision.PERMIT)
                                        .purpose(ModelProjectConsentPurpose.SEQUENCING).build()
                                )
                            ).build()
                    )
                    .build()
            )
            .build()

        kafkaInputListener.onMessage(
            ConsumerRecord(
                "testtopic",
                0,
                0,
                "",
                this.objectMapper.writeValueAsString(mtbFile)
            )
        )

        verify(requestProcessor, times(1)).processMtbFile(any<Mtb>())
    }

    @Test
    fun shouldProcessDeleteRequest() {
        whenever(consentEvaluator.check(any())).thenReturn(
            ConsentEvaluation(
                TtpConsentStatus.BROAD_CONSENT_GIVEN,
                false
            )
        )

        val mtbFile = Mtb.builder()
            .patient(Patient.builder().id("DUMMY_12345678").build())
            .metadata(
                MvhMetadata
                    .builder()
                    .modelProjectConsent(
                        ModelProjectConsent
                            .builder()
                            .provisions(
                                listOf(
                                    Provision.builder().type(ConsentProvision.DENY)
                                        .purpose(ModelProjectConsentPurpose.SEQUENCING).build()
                                )
                            ).build()
                    )
                    .build()
            )
            .build()

        kafkaInputListener.onMessage(
            ConsumerRecord(
                "testtopic",
                0,
                0,
                "",
                this.objectMapper.writeValueAsString(mtbFile)
            )
        )

        verify(requestProcessor, times(1)).processDeletion(
            anyValueClass(),
            eq(TtpConsentStatus.UNKNOWN_CHECK_FILE)
        )
    }

    @Test
    fun shouldProcessMtbFileRequestWithExistingRequestId() {
        whenever(consentEvaluator.check(any())).thenReturn(
            ConsentEvaluation(
                TtpConsentStatus.BROAD_CONSENT_GIVEN,
                true
            )
        )

        val mtbFile = Mtb.builder()
            .patient(Patient.builder().id("DUMMY_12345678").build())
            .metadata(
                MvhMetadata
                    .builder()
                    .modelProjectConsent(
                        ModelProjectConsent
                            .builder()
                            .provisions(
                                listOf(
                                    Provision.builder().type(ConsentProvision.PERMIT)
                                        .purpose(ModelProjectConsentPurpose.SEQUENCING).build()
                                )
                            ).build()
                    )
                    .build()
            )
            .build()

        val headers = RecordHeaders(listOf(RecordHeader("requestId", UUID.randomUUID().toString().toByteArray())))
        kafkaInputListener.onMessage(
            ConsumerRecord(
                "testtopic",
                0,
                0,
                -1L,
                TimestampType.NO_TIMESTAMP_TYPE,
                -1,
                -1,
                "",
                this.objectMapper.writeValueAsString(mtbFile),
                headers,
                Optional.empty()
            )
        )

        verify(requestProcessor, times(1)).processMtbFile(any<Mtb>(), anyValueClass())
    }

    @Test
    fun shouldProcessDeleteRequestWithExistingRequestId() {
        whenever(consentEvaluator.check(any())).thenReturn(
            ConsentEvaluation(
                TtpConsentStatus.BROAD_CONSENT_GIVEN,
                false
            )
        )

        val mtbFile = Mtb.builder()
            .patient(Patient.builder().id("DUMMY_12345678").build())
            .metadata(
                MvhMetadata
                    .builder()
                    .modelProjectConsent(
                        ModelProjectConsent
                            .builder()
                            .provisions(
                                listOf(
                                    Provision.builder().type(ConsentProvision.DENY)
                                        .purpose(ModelProjectConsentPurpose.SEQUENCING).build()
                                )
                            ).build()
                    )
                    .build()
            )
            .build()

        val headers = RecordHeaders(listOf(RecordHeader("requestId", UUID.randomUUID().toString().toByteArray())))
        kafkaInputListener.onMessage(
            ConsumerRecord(
                "testtopic",
                0,
                0,
                -1L,
                TimestampType.NO_TIMESTAMP_TYPE,
                -1,
                -1,
                "",
                this.objectMapper.writeValueAsString(mtbFile),
                headers,
                Optional.empty()
            )
        )
        verify(requestProcessor, times(1)).processDeletion(
            anyValueClass(), anyValueClass(), eq(
                TtpConsentStatus.UNKNOWN_CHECK_FILE
            )
        )
    }

    @Test
    fun shouldNotProcessDnpmV2Request() {
        val mtbFile = Mtb.builder()
            .patient(Patient.builder().id("DUMMY_12345678").build())
            .metadata(
                MvhMetadata
                    .builder()
                    .modelProjectConsent(
                        ModelProjectConsent
                            .builder()
                            .provisions(
                                listOf(
                                    Provision.builder().type(ConsentProvision.DENY)
                                        .purpose(ModelProjectConsentPurpose.SEQUENCING).build()
                                )
                            ).build()
                    )
                    .build()
            )
            .build()

        val headers = RecordHeaders(
            listOf(
                RecordHeader("requestId", UUID.randomUUID().toString().toByteArray()),
                RecordHeader("contentType", CustomMediaType.APPLICATION_VND_DNPM_V2_MTB_JSON_VALUE.toByteArray())
            )
        )
        kafkaInputListener.onMessage(
            ConsumerRecord(
                "testtopic",
                0,
                0,
                -1L,
                TimestampType.NO_TIMESTAMP_TYPE,
                -1,
                -1,
                "",
                this.objectMapper.writeValueAsString(mtbFile),
                headers,
                Optional.empty()
            )
        )
        verify(requestProcessor, times(0)).processDeletion(
            anyValueClass(), anyValueClass(), eq(
                TtpConsentStatus.UNKNOWN_CHECK_FILE
            )
        )
    }

}
