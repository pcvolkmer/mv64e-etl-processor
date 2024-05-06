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

package dev.dnpm.etl.processor.web

import com.gargoylesoftware.htmlunit.WebClient
import com.gargoylesoftware.htmlunit.html.HtmlPage
import dev.dnpm.etl.processor.Fingerprint
import dev.dnpm.etl.processor.NotFoundException
import dev.dnpm.etl.processor.config.AppConfiguration
import dev.dnpm.etl.processor.config.AppSecurityConfiguration
import dev.dnpm.etl.processor.monitoring.Report
import dev.dnpm.etl.processor.monitoring.Request
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.dnpm.etl.processor.monitoring.RequestType
import dev.dnpm.etl.processor.services.RequestService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.htmlunit.MockMvcWebClientBuilder
import java.io.IOException
import java.time.Instant
import java.util.*

@WebMvcTest(controllers = [HomeController::class])
@ExtendWith(value = [MockitoExtension::class, SpringExtension::class])
@ContextConfiguration(
    classes = [
        HomeController::class,
        AppConfiguration::class,
        AppSecurityConfiguration::class
    ]
)
@TestPropertySource(
    properties = [
        "app.pseudonymize.generator=BUILDIN",
        "app.security.admin-user=admin",
        "app.security.admin-password={noop}very-secret"
    ]
)
@MockBean(
    RequestService::class
)
class HomeControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var webClient: WebClient

    @BeforeEach
    fun setup(
        @Autowired mockMvc: MockMvc,
        @Autowired requestService: RequestService
    ) {
        this.mockMvc = mockMvc
        this.webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()

        whenever(requestService.findAll(any<Pageable>())).thenReturn(Page.empty())
    }

    @Test
    fun testShouldRequestHomePage() {
        mockMvc.get("/").andExpect {
            status { isOk() }
            view { name("index") }
        }
    }

    @Nested
    inner class WithRequests {

        private lateinit var requestService: RequestService

        @BeforeEach
        fun setup(
            @Autowired requestService: RequestService
        ) {
            this.requestService = requestService
        }

        @Test
        fun testShouldShowHomePage() {
            whenever(requestService.findAll(any<Pageable>())).thenReturn(
                PageImpl(
                    listOf(
                        Request(
                            2L,
                            UUID.randomUUID().toString(),
                            "PSEUDO1",
                            "PATIENT1",
                            Fingerprint("ashdkasdh"),
                            RequestType.MTB_FILE,
                            RequestStatus.SUCCESS
                        ),
                        Request(
                            1L,
                            UUID.randomUUID().toString(),
                            "PSEUDO1",
                            "PATIENT1",
                            Fingerprint("asdasdasd"),
                            RequestType.MTB_FILE,
                            RequestStatus.ERROR
                        )
                    )
                )
            )

            val page = webClient.getPage<HtmlPage>("http://localhost/")
            assertThat(
                page.querySelectorAll("tbody tr")
            ).hasSize(2)
        }

        @Test
        @WithMockUser(username = "admin", roles = ["ADMIN"])
        fun testShouldShowRequestDetails() {
            val requestId = UUID.randomUUID().toString()

            whenever(requestService.findByUuid(any())).thenReturn(
                Optional.of(
                    Request(
                        2L,
                        requestId,
                        "PSEUDO1",
                        "PATIENT1",
                        Fingerprint("ashdkasdh"),
                        RequestType.MTB_FILE,
                        RequestStatus.SUCCESS,
                        Instant.now(),
                        Report("Test")
                    )
                )
            )

            val page = webClient.getPage<HtmlPage>("http://localhost/report/${requestId}")
            assertThat(
                page.querySelectorAll("tbody tr")
            ).hasSize(1)
        }

        @Test
        @WithMockUser(username = "admin", roles = ["ADMIN"])
        fun testShouldShowPatientDetails() {
            whenever(requestService.findRequestByPatientId(anyString(), any<Pageable>())).thenReturn(
                PageImpl(
                    listOf(
                        Request(
                            2L,
                            UUID.randomUUID().toString(),
                            "PSEUDO1",
                            "PATIENT1",
                            Fingerprint("ashdkasdh"),
                            RequestType.MTB_FILE,
                            RequestStatus.SUCCESS
                        ),
                        Request(
                            1L,
                            UUID.randomUUID().toString(),
                            "PSEUDO1",
                            "PATIENT1",
                            Fingerprint("asdasdasd"),
                            RequestType.MTB_FILE,
                            RequestStatus.ERROR
                        )
                    )
                )
            )

            val page = webClient.getPage<HtmlPage>("http://localhost/patient/PSEUDO1")
            assertThat(
                page.querySelectorAll("tbody tr")
            ).hasSize(2)
        }

    }

    @Nested
    inner class WithoutRequests {

        private lateinit var requestService: RequestService

        @BeforeEach
        fun setup(
            @Autowired requestService: RequestService
        ) {
            this.requestService = requestService

            whenever(requestService.findAll(any<Pageable>())).thenReturn(Page.empty())
        }

        @Test
        fun testShouldShowHomePage() {
            val page = webClient.getPage<HtmlPage>("http://localhost/")
            assertThat(
                page.querySelectorAll("tbody tr")
            ).isEmpty()
        }

        @Test
        @WithMockUser(username = "admin", roles = ["ADMIN"])
        fun testShouldThrowNotFoundExceptionForUnknownReport() {
            val requestId = UUID.randomUUID().toString()

            whenever(requestService.findByUuid(any())).thenReturn(
                Optional.empty()
            )

            assertThrows<IOException> {
                webClient.getPage<HtmlPage>("http://localhost/report/${requestId}")
            }.also {
                assertThat(it).hasRootCauseInstanceOf(NotFoundException::class.java)
            }
        }

        @Test
        @WithMockUser(username = "admin", roles = ["ADMIN"])
        fun testShouldShowEmptyPatientDetails() {
            whenever(requestService.findRequestByPatientId(anyString(), any<Pageable>())).thenReturn(Page.empty())

            val page = webClient.getPage<HtmlPage>("http://localhost/patient/PSEUDO1")
            assertThat(
                page.querySelectorAll("tbody tr")
            ).hasSize(0)
        }
    }

}
