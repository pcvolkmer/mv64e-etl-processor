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
import dev.dnpm.etl.processor.config.AppSecurityConfiguration
import dev.dnpm.etl.processor.consent.ConsentEvaluation
import dev.dnpm.etl.processor.consent.ConsentEvaluator
import dev.dnpm.etl.processor.consent.MtbFileConsentService
import dev.dnpm.etl.processor.consent.TtpConsentStatus
import dev.dnpm.etl.processor.security.TokenRepository
import dev.dnpm.etl.processor.security.UserRoleRepository
import dev.dnpm.etl.processor.services.RequestProcessor
import dev.pcvolkmer.mv64e.mtb.*
import java.time.Instant
import java.util.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post

@WebMvcTest(controllers = [MtbFileRestController::class])
@ExtendWith(value = [MockitoExtension::class, SpringExtension::class])
@ContextConfiguration(
    classes =
        [
            MtbFileRestController::class,
            AppSecurityConfiguration::class,
            MtbFileConsentService::class,
        ]
)
@MockitoBean(types = [TokenRepository::class, RequestProcessor::class, ConsentEvaluator::class])
@TestPropertySource(
    properties =
        [
            "app.pseudonymize.generator=BUILDIN",
            "app.security.admin-user=admin",
            "app.security.admin-password={noop}very-secret",
            "app.security.enable-tokens=true",
        ]
)
class MtbFileRestControllerTest {

  lateinit var mockMvc: MockMvc
  lateinit var requestProcessor: RequestProcessor
  lateinit var consentEvaluator: ConsentEvaluator

  @BeforeEach
  fun setup(
      @Autowired mockMvc: MockMvc,
      @Autowired requestProcessor: RequestProcessor,
      @Autowired consentEvaluator: ConsentEvaluator,
  ) {
    this.mockMvc = mockMvc
    this.requestProcessor = requestProcessor
    this.consentEvaluator = consentEvaluator

    doAnswer { ConsentEvaluation(TtpConsentStatus.BROAD_CONSENT_GIVEN, true) }
        .whenever(consentEvaluator)
        .check(any())
  }

  @ParameterizedTest
  @ValueSource(
      strings =
          [
              "/mtbfile",
              "/mtbfile/etl/patient-record",
              "/mtb",
              "/mtb/etl/patient-record",
              "/api/mtbfile",
              "/api/mtbfile/etl/patient-record",
              "/api/mtb",
              "/api/mtb/etl/patient-record",
          ]
  )
  fun testShouldGrantPermissionToSendMtbFile(url: String) {
    mockMvc
        .post(url) {
          with(user("onkostarserver").roles("MTBFILE"))
          contentType = MediaType.APPLICATION_JSON
          content = ObjectMapper().writeValueAsString(mtbFile)
        }
        .andExpect { status { isAccepted() } }

    verify(requestProcessor, times(1)).processMtbFile(any<Mtb>())
  }

  @ParameterizedTest
  @ValueSource(
      strings =
          [
              "/mtbfile",
              "/mtbfile/etl/patient-record",
              "/mtb",
              "/mtb/etl/patient-record",
              "/api/mtbfile",
              "/api/mtbfile/etl/patient-record",
              "/api/mtb",
              "/api/mtb/etl/patient-record",
          ]
  )
  fun testShouldGrantPermissionToSendMtbFileToAdminUser(url: String) {
    mockMvc
        .post(url) {
          with(user("onkostarserver").roles("ADMIN"))
          contentType = MediaType.APPLICATION_JSON
          content = ObjectMapper().writeValueAsString(mtbFile)
        }
        .andExpect { status { isAccepted() } }

    verify(requestProcessor, times(1)).processMtbFile(any<Mtb>())
  }

  @ParameterizedTest
  @ValueSource(
      strings =
          [
              "/mtbfile",
              "/mtbfile/etl/patient-record",
              "/mtb",
              "/mtb/etl/patient-record",
              "/api/mtbfile",
              "/api/mtbfile/etl/patient-record",
              "/api/mtb",
              "/api/mtb/etl/patient-record",
          ]
  )
  fun testShouldDenyPermissionToSendMtbFile(url: String) {
    mockMvc
        .post(url) {
          with(anonymous())
          contentType = MediaType.APPLICATION_JSON
          content = ObjectMapper().writeValueAsString(mtbFile)
        }
        .andExpect { status { isUnauthorized() } }

    verify(requestProcessor, never()).processMtbFile(any<Mtb>())
  }

  @ParameterizedTest
  @ValueSource(
      strings =
          [
              "/mtbfile",
              "/mtbfile/etl/patient-record",
              "/mtb",
              "/mtb/etl/patient-record",
              "/api/mtbfile",
              "/api/mtbfile/etl/patient-record",
              "/api/mtb",
              "/api/mtb/etl/patient-record",
          ]
  )
  fun testShouldDenyPermissionToSendMtbFileForUser(url: String) {
    mockMvc
        .post(url) {
          with(user("fakeuser").roles("USER"))
          contentType = MediaType.APPLICATION_JSON
          content = ObjectMapper().writeValueAsString(mtbFile)
        }
        .andExpect { status { isForbidden() } }

    verify(requestProcessor, never()).processMtbFile(any<Mtb>())
  }

  @ParameterizedTest
  @ValueSource(
      strings =
          [
              "/mtbfile/TEST_12345678",
              "/mtbfile/etl/patient-record/TEST_12345678",
              "/mtbfile/etl/patient/TEST_12345678",
              "/mtb/TEST_12345678",
              "/mtb/etl/patient-record/TEST_12345678",
              "/mtb/etl/patient/TEST_12345678",
              "/api/mtbfile/TEST_12345678",
              "/api/mtbfile/etl/patient-record/TEST_12345678",
              "/api/mtbfile/etl/patient/TEST_12345678",
              "/api/mtb/TEST_12345678",
              "/api/mtb/etl/patient-record/TEST_12345678",
              "/api/mtb/etl/patient/TEST_12345678",
          ]
  )
  fun testShouldGrantPermissionToDeletePatientData(url: String) {
    mockMvc
        .delete(url) { with(user("onkostarserver").roles("MTBFILE")) }
        .andExpect { status { isAccepted() } }

    verify(requestProcessor, times(1))
        .processDeletion(anyValueClass(), eq(TtpConsentStatus.UNKNOWN_CHECK_FILE))
  }

  @ParameterizedTest
  @ValueSource(
      strings =
          [
              "/mtbfile/TEST_12345678",
              "/mtbfile/etl/patient-record/TEST_12345678",
              "/mtbfile/etl/patient/TEST_12345678",
              "/mtb/TEST_12345678",
              "/mtb/etl/patient-record/TEST_12345678",
              "/mtb/etl/patient/TEST_12345678",
              "/api/mtbfile/TEST_12345678",
              "/api/mtbfile/etl/patient-record/TEST_12345678",
              "/api/mtbfile/etl/patient/TEST_12345678",
              "/api/mtb/TEST_12345678",
              "/api/mtb/etl/patient-record/TEST_12345678",
              "/api/mtb/etl/patient/TEST_12345678",
          ]
  )
  fun testShouldDenyPermissionToDeletePatientData(url: String) {
    mockMvc.delete(url) { with(anonymous()) }.andExpect { status { isUnauthorized() } }

    verify(requestProcessor, never()).processDeletion(anyValueClass(), any())
  }

  @Nested
  @MockitoBean(types = [UserRoleRepository::class, ClientRegistrationRepository::class])
  @TestPropertySource(
      properties =
          [
              "app.pseudonymize.generator=BUILDIN",
              "app.security.admin-user=admin",
              "app.security.admin-password={noop}very-secret",
              "app.security.enable-tokens=true",
              "app.security.enable-oidc=true",
          ]
  )
  inner class WithOidcEnabled {
    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "/mtbfile",
                "/mtbfile/etl/patient-record",
                "/mtb",
                "/mtb/etl/patient-record",
                "/api/mtbfile",
                "/api/mtbfile/etl/patient-record",
                "/api/mtb",
                "/api/mtb/etl/patient-record",
            ]
    )
    fun testShouldGrantPermissionToSendMtbFileToAdminUser(url: String) {
      mockMvc
          .post(url) {
            with(user("onkostarserver").roles("ADMIN"))
            contentType = MediaType.APPLICATION_JSON
            content = ObjectMapper().writeValueAsString(mtbFile)
          }
          .andExpect { status { isAccepted() } }

      verify(requestProcessor, times(1)).processMtbFile(any<Mtb>())
    }

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "/mtbfile",
                "/mtbfile/etl/patient-record",
                "/mtb",
                "/mtb/etl/patient-record",
                "/api/mtbfile",
                "/api/mtbfile/etl/patient-record",
                "/api/mtb",
                "/api/mtb/etl/patient-record",
            ]
    )
    fun testShouldGrantPermissionToSendMtbFileToUser(url: String) {
      mockMvc
          .post(url) {
            with(user("onkostarserver").roles("USER"))
            contentType = MediaType.APPLICATION_JSON
            content = ObjectMapper().writeValueAsString(mtbFile)
          }
          .andExpect { status { isAccepted() } }

      verify(requestProcessor, times(1)).processMtbFile(any<Mtb>())
    }
  }

  companion object {

    val mtbFile =
        Mtb.builder()
            .patient(Patient.builder().id("PID").build())
            .episodesOfCare(
                listOf(
                    MtbEpisodeOfCare.builder()
                        .id("1")
                        .patient(Reference.builder().id("PID").build())
                        .period(
                            PeriodDate.builder()
                                .start(Date.from(Instant.parse("2023-08-08T02:00:00.00Z")))
                                .build()
                        )
                        .build()
                )
            )
            .build()
  }
}
