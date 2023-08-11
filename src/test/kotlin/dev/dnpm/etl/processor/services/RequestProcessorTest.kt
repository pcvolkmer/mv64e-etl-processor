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

package dev.dnpm.etl.processor.services

import com.fasterxml.jackson.databind.ObjectMapper
import de.ukw.ccc.bwhc.dto.*
import dev.dnpm.etl.processor.monitoring.Request
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.dnpm.etl.processor.monitoring.RequestType
import dev.dnpm.etl.processor.output.MtbFileSender
import dev.dnpm.etl.processor.output.RestMtbFileSender
import dev.dnpm.etl.processor.pseudonym.PseudonymizeService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.*


@ExtendWith(MockitoExtension::class)
class RequestProcessorTest {

    private lateinit var pseudonymizeService: PseudonymizeService
    private lateinit var sender: MtbFileSender
    private lateinit var requestService: RequestService
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    private lateinit var requestProcessor: RequestProcessor

    @BeforeEach
    fun setup(
        @Mock pseudonymizeService: PseudonymizeService,
        @Mock sender: RestMtbFileSender,
        @Mock requestService: RequestService,
        @Mock applicationEventPublisher: ApplicationEventPublisher
    ) {
        this.pseudonymizeService = pseudonymizeService
        this.sender = sender
        this.requestService = requestService
        this.applicationEventPublisher = applicationEventPublisher

        val objectMapper = ObjectMapper()

        requestProcessor = RequestProcessor(
            pseudonymizeService,
            sender,
            requestService,
            objectMapper,
            applicationEventPublisher
        )
    }

    @Test
    fun testShouldSendMtbFileDuplicationAndSaveUnknownRequestStatusAtFirst() {
        doAnswer {
            Request(
                id = 1L,
                uuid = UUID.randomUUID().toString(),
                patientId = "TEST_12345678901",
                pid = "P1",
                fingerprint = "xrysxpozhbs2lnrjgf3yq4fzj33kxr7xr5c2cbuskmelfdmckl3a",
                type = RequestType.MTB_FILE,
                status = RequestStatus.SUCCESS,
                processedAt = Instant.parse("2023-08-08T02:00:00Z")
            )
        }.`when`(requestService).lastMtbFileRequestForPatientPseudonym(anyString())

        doAnswer {
            false
        }.`when`(requestService).isLastRequestWithKnownStatusDeletion(anyString())

        doAnswer {
            it.arguments[0] as String
        }.`when`(pseudonymizeService).patientPseudonym(any())

        val mtbFile = MtbFile.builder()
            .withPatient(
                Patient.builder()
                    .withId("1")
                    .withBirthDate("2000-08-08")
                    .withGender(Patient.Gender.MALE)
                    .build()
            )
            .withConsent(
                Consent.builder()
                    .withId("1")
                    .withStatus(Consent.Status.ACTIVE)
                    .withPatient("123")
                    .build()
            )
            .withEpisode(
                Episode.builder()
                    .withId("1")
                    .withPatient("1")
                    .withPeriod(PeriodStart("2023-08-08"))
                    .build()
            )
            .build()

        this.requestProcessor.processMtbFile(mtbFile)

        val requestCaptor = argumentCaptor<Request>()
        verify(requestService, times(1)).save(requestCaptor.capture())
        assertThat(requestCaptor.firstValue).isNotNull
        assertThat(requestCaptor.firstValue.status).isEqualTo(RequestStatus.UNKNOWN)
    }

    @Test
    fun testShouldDetectMtbFileDuplicationAndSendDuplicationEvent() {
        doAnswer {
            Request(
                id = 1L,
                uuid = UUID.randomUUID().toString(),
                patientId = "TEST_12345678901",
                pid = "P1",
                fingerprint = "xrysxpozhbs2lnrjgf3yq4fzj33kxr7xr5c2cbuskmelfdmckl3a",
                type = RequestType.MTB_FILE,
                status = RequestStatus.SUCCESS,
                processedAt = Instant.parse("2023-08-08T02:00:00Z")
            )
        }.`when`(requestService).lastMtbFileRequestForPatientPseudonym(anyString())

        doAnswer {
            false
        }.`when`(requestService).isLastRequestWithKnownStatusDeletion(anyString())

        doAnswer {
            it.arguments[0] as String
        }.`when`(pseudonymizeService).patientPseudonym(any())

        val mtbFile = MtbFile.builder()
            .withPatient(
                Patient.builder()
                    .withId("1")
                    .withBirthDate("2000-08-08")
                    .withGender(Patient.Gender.MALE)
                    .build()
            )
            .withConsent(
                Consent.builder()
                    .withId("1")
                    .withStatus(Consent.Status.ACTIVE)
                    .withPatient("123")
                    .build()
            )
            .withEpisode(
                Episode.builder()
                    .withId("1")
                    .withPatient("1")
                    .withPeriod(PeriodStart("2023-08-08"))
                    .build()
            )
            .build()

        this.requestProcessor.processMtbFile(mtbFile)

        val eventCaptor = argumentCaptor<ResponseEvent>()
        verify(applicationEventPublisher, times(1)).publishEvent(eventCaptor.capture())
        assertThat(eventCaptor.firstValue).isNotNull
        assertThat(eventCaptor.firstValue.status).isEqualTo(RequestStatus.DUPLICATION)
    }

    @Test
    fun testShouldSendMtbFileAndSendSuccessEvent() {
        doAnswer {
            Request(
                id = 1L,
                uuid = UUID.randomUUID().toString(),
                patientId = "TEST_12345678901",
                pid = "P1",
                fingerprint = "different",
                type = RequestType.MTB_FILE,
                status = RequestStatus.SUCCESS,
                processedAt = Instant.parse("2023-08-08T02:00:00Z")
            )
        }.`when`(requestService).lastMtbFileRequestForPatientPseudonym(anyString())

        doAnswer {
            false
        }.`when`(requestService).isLastRequestWithKnownStatusDeletion(anyString())

        doAnswer {
            MtbFileSender.Response(status = RequestStatus.SUCCESS)
        }.`when`(sender).send(any<MtbFileSender.MtbFileRequest>())

        doAnswer {
            it.arguments[0] as String
        }.`when`(pseudonymizeService).patientPseudonym(any())

        val mtbFile = MtbFile.builder()
            .withPatient(
                Patient.builder()
                    .withId("1")
                    .withBirthDate("2000-08-08")
                    .withGender(Patient.Gender.MALE)
                    .build()
            )
            .withConsent(
                Consent.builder()
                    .withId("1")
                    .withStatus(Consent.Status.ACTIVE)
                    .withPatient("123")
                    .build()
            )
            .withEpisode(
                Episode.builder()
                    .withId("1")
                    .withPatient("1")
                    .withPeriod(PeriodStart("2023-08-08"))
                    .build()
            )
            .build()

        this.requestProcessor.processMtbFile(mtbFile)

        val eventCaptor = argumentCaptor<ResponseEvent>()
        verify(applicationEventPublisher, times(1)).publishEvent(eventCaptor.capture())
        assertThat(eventCaptor.firstValue).isNotNull
        assertThat(eventCaptor.firstValue.status).isEqualTo(RequestStatus.SUCCESS)
    }

    @Test
    fun testShouldSendMtbFileAndSendErrorEvent() {
        doAnswer {
            Request(
                id = 1L,
                uuid = UUID.randomUUID().toString(),
                patientId = "TEST_12345678901",
                pid = "P1",
                fingerprint = "different",
                type = RequestType.MTB_FILE,
                status = RequestStatus.SUCCESS,
                processedAt = Instant.parse("2023-08-08T02:00:00Z")
            )
        }.`when`(requestService).lastMtbFileRequestForPatientPseudonym(anyString())

        doAnswer {
            false
        }.`when`(requestService).isLastRequestWithKnownStatusDeletion(anyString())

        doAnswer {
            MtbFileSender.Response(status = RequestStatus.ERROR)
        }.`when`(sender).send(any<MtbFileSender.MtbFileRequest>())

        doAnswer {
            it.arguments[0] as String
        }.`when`(pseudonymizeService).patientPseudonym(any())

        val mtbFile = MtbFile.builder()
            .withPatient(
                Patient.builder()
                    .withId("1")
                    .withBirthDate("2000-08-08")
                    .withGender(Patient.Gender.MALE)
                    .build()
            )
            .withConsent(
                Consent.builder()
                    .withId("1")
                    .withStatus(Consent.Status.ACTIVE)
                    .withPatient("123")
                    .build()
            )
            .withEpisode(
                Episode.builder()
                    .withId("1")
                    .withPatient("1")
                    .withPeriod(PeriodStart("2023-08-08"))
                    .build()
            )
            .build()

        this.requestProcessor.processMtbFile(mtbFile)

        val eventCaptor = argumentCaptor<ResponseEvent>()
        verify(applicationEventPublisher, times(1)).publishEvent(eventCaptor.capture())
        assertThat(eventCaptor.firstValue).isNotNull
        assertThat(eventCaptor.firstValue.status).isEqualTo(RequestStatus.ERROR)
    }

    @Test
    fun testShouldSendDeleteRequestAndSaveUnknownRequestStatusAtFirst() {
        doAnswer {
            "PSEUDONYM"
        }.`when`(pseudonymizeService).patientPseudonym(anyString())

        doAnswer {
            MtbFileSender.Response(status = RequestStatus.UNKNOWN)
        }.`when`(sender).send(any<MtbFileSender.DeleteRequest>())

        this.requestProcessor.processDeletion("TEST_12345678901")

        val requestCaptor = argumentCaptor<Request>()
        verify(requestService, times(1)).save(requestCaptor.capture())
        assertThat(requestCaptor.firstValue).isNotNull
        assertThat(requestCaptor.firstValue.status).isEqualTo(RequestStatus.UNKNOWN)
    }

    @Test
    fun testShouldSendDeleteRequestAndSendSuccessEvent() {
        doAnswer {
            "PSEUDONYM"
        }.`when`(pseudonymizeService).patientPseudonym(anyString())

        doAnswer {
            MtbFileSender.Response(status = RequestStatus.SUCCESS)
        }.`when`(sender).send(any<MtbFileSender.DeleteRequest>())

        this.requestProcessor.processDeletion("TEST_12345678901")

        val eventCaptor = argumentCaptor<ResponseEvent>()
        verify(applicationEventPublisher, times(1)).publishEvent(eventCaptor.capture())
        assertThat(eventCaptor.firstValue).isNotNull
        assertThat(eventCaptor.firstValue.status).isEqualTo(RequestStatus.SUCCESS)
    }

    @Test
    fun testShouldSendDeleteRequestAndSendErrorEvent() {
        doAnswer {
            "PSEUDONYM"
        }.`when`(pseudonymizeService).patientPseudonym(anyString())

        doAnswer {
            MtbFileSender.Response(status = RequestStatus.ERROR)
        }.`when`(sender).send(any<MtbFileSender.DeleteRequest>())

        this.requestProcessor.processDeletion("TEST_12345678901")

        val eventCaptor = argumentCaptor<ResponseEvent>()
        verify(applicationEventPublisher, times(1)).publishEvent(eventCaptor.capture())
        assertThat(eventCaptor.firstValue).isNotNull
        assertThat(eventCaptor.firstValue.status).isEqualTo(RequestStatus.ERROR)
    }

    @Test
    fun testShouldSendDeleteRequestWithPseudonymErrorAndSaveErrorRequestStatus() {
        doThrow(RuntimeException()).`when`(pseudonymizeService).patientPseudonym(anyString())

        this.requestProcessor.processDeletion("TEST_12345678901")

        val requestCaptor = argumentCaptor<Request>()
        verify(requestService, times(1)).save(requestCaptor.capture())
        assertThat(requestCaptor.firstValue).isNotNull
        assertThat(requestCaptor.firstValue.status).isEqualTo(RequestStatus.ERROR)
    }

}