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
import dev.dnpm.etl.processor.Fingerprint
import dev.dnpm.etl.processor.PatientId
import dev.dnpm.etl.processor.PatientPseudonym
import dev.dnpm.etl.processor.config.AppConfigProperties
import dev.dnpm.etl.processor.consent.TtpConsentStatus
import dev.dnpm.etl.processor.monitoring.Request
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.dnpm.etl.processor.monitoring.RequestType
import dev.dnpm.etl.processor.output.DeleteRequest
import dev.dnpm.etl.processor.output.DnpmV2MtbFileRequest
import dev.dnpm.etl.processor.output.MtbFileSender
import dev.dnpm.etl.processor.output.RestMtbFileSender
import dev.dnpm.etl.processor.pseudonym.PseudonymizeService
import dev.dnpm.etl.processor.randomRequestId
import dev.pcvolkmer.mv64e.mtb.*
import java.time.Instant
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyValueClass
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher

@ExtendWith(MockitoExtension::class)
class RequestProcessorTest {

  private lateinit var pseudonymizeService: PseudonymizeService
  private lateinit var transformationService: TransformationService
  private lateinit var sender: MtbFileSender
  private lateinit var requestService: RequestService
  private lateinit var applicationEventPublisher: ApplicationEventPublisher
  private lateinit var appConfigProperties: AppConfigProperties
  private lateinit var consentProcessor: ConsentProcessor
  private lateinit var requestProcessor: RequestProcessor

  @BeforeEach
  fun setup(
      @Mock pseudonymizeService: PseudonymizeService,
      @Mock transformationService: TransformationService,
      @Mock sender: RestMtbFileSender,
      @Mock requestService: RequestService,
      @Mock applicationEventPublisher: ApplicationEventPublisher,
      @Mock consentProcessor: ConsentProcessor,
  ) {
    this.pseudonymizeService = pseudonymizeService
    this.transformationService = transformationService
    this.sender = sender
    this.requestService = requestService
    this.applicationEventPublisher = applicationEventPublisher
    this.appConfigProperties = AppConfigProperties()
    this.consentProcessor = consentProcessor

    val objectMapper = ObjectMapper()

    requestProcessor =
        RequestProcessor(
            pseudonymizeService,
            transformationService,
            sender,
            requestService,
            objectMapper,
            applicationEventPublisher,
            appConfigProperties,
            consentProcessor,
        )
  }

  @Test
  fun testShouldSendMtbFileDuplicationAndSaveUnknownRequestStatusAtFirst() {
    doAnswer {
          Request(
              1L,
              randomRequestId(),
              PatientPseudonym("TEST_12345678901"),
              PatientId("P1"),
              Fingerprint("6vkiti5bk6ikwifpajpt7cygmd3dvw54d6lwfhzlynb3pqtzferq"),
              RequestType.MTB_FILE,
              RequestStatus.SUCCESS,
              Instant.parse("2023-08-08T02:00:00Z"),
          )
        }
        .whenever(requestService)
        .lastMtbFileRequestForPatientPseudonym(anyValueClass())

    doAnswer { false }
        .whenever(requestService)
        .isLastRequestWithKnownStatusDeletion(anyValueClass())

    doAnswer { it.arguments[0] as String }
        .whenever(pseudonymizeService)
        .patientPseudonym(anyValueClass())

    doAnswer { it.arguments[0] }.whenever(transformationService).transform(any<Mtb>())

    whenever(consentProcessor.consentGatedCheckAndTryEmbedding(any())).thenReturn(true)

    val mtbFile =
        Mtb.builder()
            .patient(Patient.builder().id("123").build())
            .episodesOfCare(
                listOf(
                    MtbEpisodeOfCare.builder()
                        .id("1")
                        .patient(Reference.builder().id("123").build())
                        .period(
                            PeriodDate.builder()
                                .start(Date.from(Instant.parse("2023-08-08T02:00:00.00Z")))
                                .build()
                        )
                        .build()
                )
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
              1L,
              randomRequestId(),
              PatientPseudonym("TEST_12345678901"),
              PatientId("P1"),
              Fingerprint("4gcjwtjjtcczybsljxepdfpkaeusvd7g3vogfqpmphyffyzfx7dq"),
              RequestType.MTB_FILE,
              RequestStatus.SUCCESS,
              Instant.parse("2023-08-08T02:00:00Z"),
          )
        }
        .whenever(requestService)
        .lastMtbFileRequestForPatientPseudonym(anyValueClass())

    doAnswer { false }
        .whenever(requestService)
        .isLastRequestWithKnownStatusDeletion(anyValueClass())

    doAnswer { it.arguments[0] as String }
        .whenever(pseudonymizeService)
        .patientPseudonym(anyValueClass())

    doAnswer { it.arguments[0] }.whenever(transformationService).transform(any<Mtb>())

    whenever(consentProcessor.consentGatedCheckAndTryEmbedding(any())).thenReturn(true)

    val mtbFile =
        Mtb.builder()
            .patient(Patient.builder().id("123").build())
            .episodesOfCare(
                listOf(
                    MtbEpisodeOfCare.builder()
                        .id("1")
                        .patient(Reference.builder().id("123").build())
                        .period(
                            PeriodDate.builder()
                                .start(Date.from(Instant.parse("2021-01-01T00:00:00.00Z")))
                                .build()
                        )
                        .build()
                )
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
              1L,
              randomRequestId(),
              PatientPseudonym("TEST_12345678901"),
              PatientId("P1"),
              Fingerprint("different"),
              RequestType.MTB_FILE,
              RequestStatus.SUCCESS,
              Instant.parse("2023-08-08T02:00:00Z"),
          )
        }
        .whenever(requestService)
        .lastMtbFileRequestForPatientPseudonym(anyValueClass())

    doAnswer { false }
        .whenever(requestService)
        .isLastRequestWithKnownStatusDeletion(anyValueClass())

    doAnswer { MtbFileSender.Response(status = RequestStatus.SUCCESS) }
        .whenever(sender)
        .send(any<DnpmV2MtbFileRequest>())

    doAnswer { it.arguments[0] as String }
        .whenever(pseudonymizeService)
        .patientPseudonym(anyValueClass())

    doAnswer { it.arguments[0] }.whenever(transformationService).transform(any<Mtb>())

    whenever(consentProcessor.consentGatedCheckAndTryEmbedding(any())).thenReturn(true)

    val mtbFile =
        Mtb.builder()
            .patient(Patient.builder().id("123").build())
            .episodesOfCare(
                listOf(
                    MtbEpisodeOfCare.builder()
                        .id("1")
                        .patient(Reference.builder().id("123").build())
                        .period(
                            PeriodDate.builder()
                                .start(Date.from(Instant.parse("2021-01-01T00:00:00.00Z")))
                                .build()
                        )
                        .build()
                )
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
              1L,
              randomRequestId(),
              PatientPseudonym("TEST_12345678901"),
              PatientId("P1"),
              Fingerprint("different"),
              RequestType.MTB_FILE,
              RequestStatus.SUCCESS,
              Instant.parse("2023-08-08T02:00:00Z"),
          )
        }
        .whenever(requestService)
        .lastMtbFileRequestForPatientPseudonym(anyValueClass())

    doAnswer { false }
        .whenever(requestService)
        .isLastRequestWithKnownStatusDeletion(anyValueClass())

    doAnswer { MtbFileSender.Response(status = RequestStatus.ERROR) }
        .whenever(sender)
        .send(any<DnpmV2MtbFileRequest>())

    doAnswer { it.arguments[0] as String }
        .whenever(pseudonymizeService)
        .patientPseudonym(anyValueClass())

    doAnswer { it.arguments[0] }.whenever(transformationService).transform(any<Mtb>())

    whenever(consentProcessor.consentGatedCheckAndTryEmbedding(any())).thenReturn(true)

    val mtbFile =
        Mtb.builder()
            .patient(Patient.builder().id("123").build())
            .metadata(
                MvhMetadata.builder()
                    .modelProjectConsent(
                        ModelProjectConsent.builder()
                            .provisions(
                                listOf(
                                    Provision.builder()
                                        .type(ConsentProvision.PERMIT)
                                        .purpose(ModelProjectConsentPurpose.SEQUENCING)
                                        .build()
                                )
                            )
                            .build()
                    )
                    .build()
            )
            .episodesOfCare(
                listOf(
                    MtbEpisodeOfCare.builder()
                        .id("1")
                        .patient(Reference.builder().id("123").build())
                        .period(
                            PeriodDate.builder()
                                .start(Date.from(Instant.parse("2021-01-01T00:00:00.00Z")))
                                .build()
                        )
                        .build()
                )
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
    doAnswer { "PSEUDONYM" }.whenever(pseudonymizeService).patientPseudonym(anyValueClass())

    doAnswer { MtbFileSender.Response(status = RequestStatus.UNKNOWN) }
        .whenever(sender)
        .send(any<DeleteRequest>())

    this.requestProcessor.processDeletion(
        TEST_PATIENT_ID,
        isConsented = TtpConsentStatus.UNKNOWN_CHECK_FILE,
    )

    val requestCaptor = argumentCaptor<Request>()
    verify(requestService, times(1)).save(requestCaptor.capture())
    assertThat(requestCaptor.firstValue).isNotNull
    assertThat(requestCaptor.firstValue.status).isEqualTo(RequestStatus.UNKNOWN)
  }

  @Test
  fun testShouldSendDeleteRequestAndSendSuccessEvent() {
    doAnswer { "PSEUDONYM" }.whenever(pseudonymizeService).patientPseudonym(anyValueClass())

    doAnswer { MtbFileSender.Response(status = RequestStatus.SUCCESS) }
        .whenever(sender)
        .send(any<DeleteRequest>())

    this.requestProcessor.processDeletion(
        TEST_PATIENT_ID,
        isConsented = TtpConsentStatus.UNKNOWN_CHECK_FILE,
    )

    val eventCaptor = argumentCaptor<ResponseEvent>()
    verify(applicationEventPublisher, times(1)).publishEvent(eventCaptor.capture())
    assertThat(eventCaptor.firstValue).isNotNull
    assertThat(eventCaptor.firstValue.status).isEqualTo(RequestStatus.SUCCESS)
  }

  @Test
  fun testShouldSendDeleteRequestAndSendErrorEvent() {
    doAnswer { "PSEUDONYM" }.whenever(pseudonymizeService).patientPseudonym(anyValueClass())

    doAnswer { MtbFileSender.Response(status = RequestStatus.ERROR) }
        .whenever(sender)
        .send(any<DeleteRequest>())

    this.requestProcessor.processDeletion(
        TEST_PATIENT_ID,
        isConsented = TtpConsentStatus.UNKNOWN_CHECK_FILE,
    )

    val eventCaptor = argumentCaptor<ResponseEvent>()
    verify(applicationEventPublisher, times(1)).publishEvent(eventCaptor.capture())
    assertThat(eventCaptor.firstValue).isNotNull
    assertThat(eventCaptor.firstValue.status).isEqualTo(RequestStatus.ERROR)
  }

  @Test
  fun testShouldSendDeleteRequestWithPseudonymErrorAndSaveErrorRequestStatus() {
    doThrow(RuntimeException()).whenever(pseudonymizeService).patientPseudonym(anyValueClass())

    this.requestProcessor.processDeletion(
        TEST_PATIENT_ID,
        isConsented = TtpConsentStatus.UNKNOWN_CHECK_FILE,
    )

    val requestCaptor = argumentCaptor<Request>()
    verify(requestService, times(1)).save(requestCaptor.capture())
    assertThat(requestCaptor.firstValue).isNotNull
    assertThat(requestCaptor.firstValue.status).isEqualTo(RequestStatus.ERROR)
  }

  @Test
  fun testShouldNotDetectMtbFileDuplicationIfDuplicationNotConfigured() {
    this.appConfigProperties.duplicationDetection = false

    doAnswer { it.arguments[0] as String }
        .whenever(pseudonymizeService)
        .patientPseudonym(anyValueClass())

    doAnswer { it.arguments[0] }.whenever(transformationService).transform(any<Mtb>())

    doAnswer { MtbFileSender.Response(status = RequestStatus.SUCCESS) }
        .whenever(sender)
        .send(any<DnpmV2MtbFileRequest>())

    whenever(consentProcessor.consentGatedCheckAndTryEmbedding(any())).thenReturn(true)

    val mtbFile =
        Mtb.builder()
            .patient(Patient.builder().id("123").build())
            .episodesOfCare(
                listOf(
                    MtbEpisodeOfCare.builder()
                        .id("1")
                        .patient(Reference.builder().id("123").build())
                        .period(
                            PeriodDate.builder()
                                .start(Date.from(Instant.parse("2021-01-01T00:00:00.00Z")))
                                .build()
                        )
                        .build()
                )
            )
            .build()

    this.requestProcessor.processMtbFile(mtbFile)

    val eventCaptor = argumentCaptor<ResponseEvent>()
    verify(applicationEventPublisher, times(1)).publishEvent(eventCaptor.capture())
    assertThat(eventCaptor.firstValue).isNotNull
    assertThat(eventCaptor.firstValue.status).isEqualTo(RequestStatus.SUCCESS)
  }

  companion object {
    val TEST_PATIENT_ID = PatientId("TEST_12345678901")
  }
}
