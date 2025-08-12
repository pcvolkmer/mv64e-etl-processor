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
import dev.dnpm.etl.processor.anyValueClass
import dev.dnpm.etl.processor.config.AppSecurityConfiguration
import dev.dnpm.etl.processor.consent.ConsentByMtbFile
import dev.dnpm.etl.processor.consent.TtpConsentStatus
import dev.dnpm.etl.processor.consent.IGetConsent
import dev.dnpm.etl.processor.security.TokenRepository
import dev.dnpm.etl.processor.security.UserRoleRepository
import dev.dnpm.etl.processor.services.RequestProcessor
import dev.pcvolkmer.mv64e.mtb.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
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
import java.time.Instant
import java.util.*

@WebMvcTest(controllers = [MtbFileRestController::class])
@ExtendWith(value = [MockitoExtension::class, SpringExtension::class])
@ContextConfiguration(
    classes = [
        MtbFileRestController::class,
        AppSecurityConfiguration::class,
        ConsentByMtbFile::class, IGetConsent::class
    ]
)
@MockitoBean(types = [TokenRepository::class, RequestProcessor::class])
@TestPropertySource(
    properties = [
        "app.pseudonymize.generator=BUILDIN",
        "app.security.admin-user=admin",
        "app.security.admin-password={noop}very-secret",
        "app.security.enable-tokens=true",
        "app.consent.gics.enabled=false"
    ]
)
class MtbFileRestControllerTest {

    private lateinit var mockMvc: MockMvc

    private lateinit var requestProcessor: RequestProcessor

    @BeforeEach
    fun setup(
        @Autowired mockMvc: MockMvc,
        @Autowired requestProcessor: RequestProcessor
    ) {
        this.mockMvc = mockMvc
        this.requestProcessor = requestProcessor
    }

    @Test
    fun testShouldGrantPermissionToSendMtbFile() {
        mockMvc.post("/mtbfile") {
            with(user("onkostarserver").roles("MTBFILE"))
            contentType = MediaType.APPLICATION_JSON
            content = ObjectMapper().writeValueAsString(mtbFile)
        }.andExpect {
            status { isAccepted() }
        }

        verify(requestProcessor, times(1)).processMtbFile(any<Mtb>())
    }

    @Test
    fun testShouldGrantPermissionToSendMtbFileToAdminUser() {
        mockMvc.post("/mtbfile") {
            with(user("onkostarserver").roles("ADMIN"))
            contentType = MediaType.APPLICATION_JSON
            content = ObjectMapper().writeValueAsString(mtbFile)
        }.andExpect {
            status { isAccepted() }
        }

        verify(requestProcessor, times(1)).processMtbFile(any<Mtb>())
    }

    @Test
    fun testShouldDenyPermissionToSendMtbFile() {
        mockMvc.post("/mtbfile") {
            with(anonymous())
            contentType = MediaType.APPLICATION_JSON
            content = ObjectMapper().writeValueAsString(mtbFile)
        }.andExpect {
            status { isUnauthorized() }
        }

        verify(requestProcessor, never()).processMtbFile(any<Mtb>())
    }

    @Test
    fun testShouldDenyPermissionToSendMtbFileForUser() {
        mockMvc.post("/mtbfile") {
            with(user("fakeuser").roles("USER"))
            contentType = MediaType.APPLICATION_JSON
            content = ObjectMapper().writeValueAsString(mtbFile)
        }.andExpect {
            status { isForbidden() }
        }

        verify(requestProcessor, never()).processMtbFile(any<Mtb>())
    }

    @Test
    fun testShouldGrantPermissionToDeletePatientData() {
        mockMvc.delete("/mtbfile/12345678") {
            with(user("onkostarserver").roles("MTBFILE"))
        }.andExpect {
            status { isAccepted() }
        }

        verify(requestProcessor, times(1)).processDeletion(anyValueClass(),  eq(TtpConsentStatus.UNKNOWN_CHECK_FILE))
    }

    @Test
    fun testShouldDenyPermissionToDeletePatientData() {
        mockMvc.delete("/mtbfile/12345678") {
            with(anonymous())
        }.andExpect {
            status { isUnauthorized() }
        }

        verify(requestProcessor, never()).processDeletion(anyValueClass(), any())
    }

    @Nested
    @MockitoBean(types = [UserRoleRepository::class, ClientRegistrationRepository::class])
    @TestPropertySource(
        properties = [
            "app.pseudonymize.generator=BUILDIN",
            "app.security.admin-user=admin",
            "app.security.admin-password={noop}very-secret",
            "app.security.enable-tokens=true",
            "app.security.enable-oidc=true",
            "app.consent.gics.enabled=false"
        ]
    )
    inner class WithOidcEnabled {
        @Test
        fun testShouldGrantPermissionToSendMtbFileToAdminUser() {
            mockMvc.post("/mtbfile") {
                with(user("onkostarserver").roles("ADMIN"))
                contentType = MediaType.APPLICATION_JSON
                content = ObjectMapper().writeValueAsString(mtbFile)
            }.andExpect {
                status { isAccepted() }
            }

            verify(requestProcessor, times(1)).processMtbFile(any<Mtb>())
        }

        @Test
        fun testShouldGrantPermissionToSendMtbFileToUser() {
            mockMvc.post("/mtbfile") {
                with(user("onkostarserver").roles("USER"))
                contentType = MediaType.APPLICATION_JSON
                content = ObjectMapper().writeValueAsString(mtbFile)
            }.andExpect {
                status { isAccepted() }
            }

            verify(requestProcessor, times(1)).processMtbFile(any<Mtb>())
        }
    }

    companion object {

        val mtbFile = Mtb.builder()
            .patient(
                Patient.builder()
                    .id("PID")
                    .build()
            )
            .episodesOfCare(
                listOf(
                    MtbEpisodeOfCare.builder()
                        .id("1")
                        .patient(Reference.builder().id("PID").build())
                        .period(PeriodDate.builder().start(Date.from(Instant.parse("2023-08-08T02:00:00.00Z"))).build())
                        .build()
                )
            )
            .build()

    }

}
