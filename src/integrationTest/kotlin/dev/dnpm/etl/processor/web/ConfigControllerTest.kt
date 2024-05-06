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
import dev.dnpm.etl.processor.config.AppConfiguration
import dev.dnpm.etl.processor.config.AppSecurityConfiguration
import dev.dnpm.etl.processor.monitoring.GPasConnectionCheckService
import dev.dnpm.etl.processor.monitoring.RestConnectionCheckService
import dev.dnpm.etl.processor.output.MtbFileSender
import dev.dnpm.etl.processor.pseudonym.Generator
import dev.dnpm.etl.processor.security.Role
import dev.dnpm.etl.processor.services.RequestProcessor
import dev.dnpm.etl.processor.services.TokenService
import dev.dnpm.etl.processor.services.TransformationService
import dev.dnpm.etl.processor.services.UserRoleService
import org.assertj.core.api.Assertions.assertThat
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
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.*
import org.springframework.test.web.servlet.htmlunit.MockMvcWebClientBuilder
import reactor.core.publisher.Sinks

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
@MockBean(name = "configsUpdateProducer", classes = [MockSink::class])
@MockBean(
    Generator::class,
    MtbFileSender::class,
    RequestProcessor::class,
    TransformationService::class,
    GPasConnectionCheckService::class,
    RestConnectionCheckService::class,
    UserRoleService::class
)
class ConfigControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var webClient: WebClient

    private lateinit var requestProcessor: RequestProcessor
    private lateinit var userRoleService: UserRoleService

    @BeforeEach
    fun setup(
        @Autowired mockMvc: MockMvc,
        @Autowired requestProcessor: RequestProcessor,
        @Autowired userRoleService: UserRoleService
    ) {
        this.mockMvc = mockMvc
        this.webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()
        this.requestProcessor = requestProcessor
        this.userRoleService = userRoleService

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
    @MockBean(
        TokenService::class
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
    inner class WithUserRolesEnabled {
        @BeforeEach
        fun setup() {
            webClient.options.isThrowExceptionOnScriptError = false
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

}
