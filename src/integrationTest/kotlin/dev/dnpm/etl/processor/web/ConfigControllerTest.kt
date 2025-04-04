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

import dev.dnpm.etl.processor.config.AppConfiguration
import dev.dnpm.etl.processor.config.AppSecurityConfiguration
import dev.dnpm.etl.processor.monitoring.ConnectionCheckResult
import dev.dnpm.etl.processor.monitoring.GPasConnectionCheckService
import dev.dnpm.etl.processor.monitoring.RestConnectionCheckService
import dev.dnpm.etl.processor.output.MtbFileSender
import dev.dnpm.etl.processor.pseudonym.Generator
import dev.dnpm.etl.processor.security.Role
import dev.dnpm.etl.processor.security.TokenService
import dev.dnpm.etl.processor.security.UserRoleService
import dev.dnpm.etl.processor.services.RequestProcessor
import dev.dnpm.etl.processor.services.TransformationService
import org.assertj.core.api.Assertions.assertThat
import org.htmlunit.WebClient
import org.htmlunit.html.HtmlPage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.MediaType.TEXT_EVENT_STREAM
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.servlet.*
import org.springframework.test.web.servlet.client.MockMvcWebTestClient
import org.springframework.test.web.servlet.htmlunit.MockMvcWebClientBuilder
import org.springframework.web.context.WebApplicationContext
import reactor.core.publisher.Sinks
import reactor.test.StepVerifier
import java.time.Instant

abstract class MockSink : Sinks.Many<Boolean>

@WebMvcTest(controllers = [ConfigController::class])
@ExtendWith(value = [MockitoExtension::class, SpringExtension::class])
@ContextConfiguration(
    classes = [
        ConfigController::class,
        AppConfiguration::class,
        AppSecurityConfiguration::class
    ]
)
@TestPropertySource(
    properties = [
        "app.pseudonymize.generator=BUILDIN"
    ]
)
@MockitoBean(name = "configsUpdateProducer", types = [MockSink::class])
@MockitoBean(
    types = [
        Generator::class,
        MtbFileSender::class,
        RequestProcessor::class,
        TransformationService::class,
        GPasConnectionCheckService::class,
        RestConnectionCheckService::class
    ]
)
class ConfigControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var webClient: WebClient

    private lateinit var requestProcessor: RequestProcessor
    private lateinit var connectionCheckUpdateProducer: Sinks.Many<ConnectionCheckResult>

    @BeforeEach
    fun setup(
        @Autowired mockMvc: MockMvc,
        @Autowired requestProcessor: RequestProcessor,
        @Autowired connectionCheckUpdateProducer: Sinks.Many<ConnectionCheckResult>
    ) {
        this.mockMvc = mockMvc
        this.webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()
        this.requestProcessor = requestProcessor
        this.connectionCheckUpdateProducer = connectionCheckUpdateProducer

        webClient.options.isThrowExceptionOnScriptError = false
    }

    @Test
    fun testShouldRequestConfigPageIfLoggedIn() {
        mockMvc.get("/configs") {
            with(user("admin").roles("ADMIN"))
            accept(MediaType.TEXT_HTML)
        }.andExpect {
            status { isOk() }
            view { name("configs") }
        }
    }

    @Test
    fun testShouldRedirectToLoginPageIfNotLoggedIn() {
        mockMvc.get("/configs") {
            with(anonymous())
            accept(MediaType.TEXT_HTML)
        }.andExpect {
            status { isFound() }
            header {
                stringValues(HttpHeaders.LOCATION, "http://localhost/login")
            }
        }
    }

    @Nested
    @TestPropertySource(
        properties = [
            "app.security.enable-tokens=true",
            "app.security.admin-user=admin"
        ]
    )
    @MockitoBean(
        types = [
            TokenService::class
        ]
    )
    inner class WithTokensEnabled {
        private lateinit var tokenService: TokenService

        @BeforeEach
        fun setup(
            @Autowired tokenService: TokenService
        ) {
            webClient.options.isThrowExceptionOnScriptError = false

            this.tokenService = tokenService
        }

        @Test
        fun testShouldSaveNewToken() {
            mockMvc.post("/configs/tokens") {
                with(user("admin").roles("ADMIN"))
                accept(MediaType.TEXT_HTML)
                contentType = MediaType.APPLICATION_FORM_URLENCODED
                content = "name=Testtoken"
            }.andExpect {
                status { is2xxSuccessful() }
                view { name("configs/tokens") }
            }

            val captor = argumentCaptor<String>()
            verify(tokenService, times(1)).addToken(captor.capture())

            assertThat(captor.firstValue).isEqualTo("Testtoken")
        }

        @Test
        fun testShouldNotSaveTokenWithExstingName() {
            whenever(tokenService.addToken(anyString())).thenReturn(Result.failure(RuntimeException("Testfailure")))

            mockMvc.post("/configs/tokens") {
                with(user("admin").roles("ADMIN"))
                accept(MediaType.TEXT_HTML)
                contentType = MediaType.APPLICATION_FORM_URLENCODED
                content = "name=Testtoken"
            }.andExpect {
                status { is2xxSuccessful() }
                view { name("configs/tokens") }
            }

            val captor = argumentCaptor<String>()
            verify(tokenService, times(1)).addToken(captor.capture())

            assertThat(captor.firstValue).isEqualTo("Testtoken")
        }

        @Test
        fun testShouldDeleteToken() {
            mockMvc.delete("/configs/tokens/42") {
                with(user("admin").roles("ADMIN"))
                accept(MediaType.TEXT_HTML)
            }.andExpect {
                status { is2xxSuccessful() }
                view { name("configs/tokens") }
            }

            val captor = argumentCaptor<Long>()
            verify(tokenService, times(1)).deleteToken(captor.capture())

            assertThat(captor.firstValue).isEqualTo(42)
        }

        @Test
        @WithMockUser(username = "admin", roles = ["ADMIN"])
        fun testShouldRenderConfigPageWithTokens() {
            val page = webClient.getPage<HtmlPage>("http://localhost/configs")
            assertThat(
                page.getElementById("tokens")
            ).isNotNull
        }
    }

    @Nested
    @TestPropertySource(
        properties = [
            "app.security.enable-tokens=false"
        ]
    )
    inner class WithTokensDisabled {
        @BeforeEach
        fun setup() {
            webClient.options.isThrowExceptionOnScriptError = false
        }

        @Test
        @WithMockUser(username = "admin", roles = ["ADMIN"])
        fun testShouldRenderConfigPageWithoutTokens() {
            val page = webClient.getPage<HtmlPage>("http://localhost/configs")
            assertThat(
                page.getElementById("tokens")
            ).isNull()
        }
    }

    @Nested
    @TestPropertySource(
        properties = [
            "app.security.enable-tokens=false",
            "app.security.admin-user=admin",
            "app.security.admin-password={noop}very-secret"
        ]
    )
    @MockitoBean(
        types = [
            UserRoleService::class
        ]
    )
    inner class WithUserRolesEnabled {
        private lateinit var userRoleService: UserRoleService

        @BeforeEach
        fun setup(
            @Autowired userRoleService: UserRoleService
        ) {
            webClient.options.isThrowExceptionOnScriptError = false

            this.userRoleService = userRoleService
        }

        @Test
        fun testShouldDeleteUserRole() {
            mockMvc.delete("/configs/userroles/42") {
                with(user("admin").roles("ADMIN"))
                accept(MediaType.TEXT_HTML)
            }.andExpect {
                status { is2xxSuccessful() }
                view { name("configs/userroles") }
            }

            val captor = argumentCaptor<Long>()
            verify(userRoleService, times(1)).deleteUserRole(captor.capture())

            assertThat(captor.firstValue).isEqualTo(42)
        }

        @Test
        fun testShouldUpdateUserRole() {
            mockMvc.put("/configs/userroles/42") {
                with(user("admin").roles("ADMIN"))
                accept(MediaType.TEXT_HTML)
                contentType = MediaType.APPLICATION_FORM_URLENCODED
                content = "role=ADMIN"
            }.andExpect {
                status { is2xxSuccessful() }
                view { name("configs/userroles") }
            }

            val idCaptor = argumentCaptor<Long>()
            val roleCaptor = argumentCaptor<Role>()
            verify(userRoleService, times(1)).updateUserRole(idCaptor.capture(), roleCaptor.capture())

            assertThat(idCaptor.firstValue).isEqualTo(42)
            assertThat(roleCaptor.firstValue).isEqualTo(Role.ADMIN)
        }

        @Test
        @WithMockUser(username = "admin", roles = ["ADMIN"])
        fun testShouldRenderConfigPageWithUserRoles() {
            val page = webClient.getPage<HtmlPage>("http://localhost/configs")
            assertThat(
                page.getElementById("userroles")
            ).isNotNull
        }
    }

    @Nested
    inner class WithUserRolesDisabled {
        @BeforeEach
        fun setup() {
            webClient.options.isThrowExceptionOnScriptError = false
        }

        @Test
        fun testShouldRenderConfigPageWithoutUserRoles() {
            val page = webClient.getPage<HtmlPage>("http://localhost/configs")
            assertThat(
                page.getElementById("userroles")
            ).isNull()
        }
    }

    @Nested
    inner class SseTest {
        private lateinit var webClient: WebTestClient

        @BeforeEach
        fun setup(
            applicationContext: WebApplicationContext,
        ) {
            this.webClient = MockMvcWebTestClient
                .bindToApplicationContext(applicationContext).build()
        }

        @Test
        fun testShouldRequestSSE() {
            val expectedEvent = ConnectionCheckResult.GPasConnectionCheckResult(true, Instant.now(), Instant.now())

            connectionCheckUpdateProducer.tryEmitNext(expectedEvent)
            connectionCheckUpdateProducer.emitComplete { _, _ -> true }

            val result = webClient.get().uri("http://localhost/configs/events").accept(TEXT_EVENT_STREAM).exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(TEXT_EVENT_STREAM)
                .returnResult(ConnectionCheckResult.GPasConnectionCheckResult::class.java)

            StepVerifier.create(result.responseBody)
                .expectNext(expectedEvent)
                .expectComplete()
                .verify()
        }
    }

}
