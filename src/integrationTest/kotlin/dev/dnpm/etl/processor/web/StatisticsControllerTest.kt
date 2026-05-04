/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2023       Comprehensive Cancer Center Mainfranken
 * Copyright (c) 2024-2026  Paul-Christian Volkmer, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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
import dev.dnpm.etl.processor.services.RequestService
import org.htmlunit.WebClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.htmlunit.MockMvcWebClientBuilder

@WebMvcTest(controllers = [StatisticsController::class])
@ExtendWith(value = [MockitoExtension::class, SpringExtension::class])
@ContextConfiguration(
    classes =
        [StatisticsController::class, AppConfiguration::class, AppSecurityConfiguration::class],
)
@TestPropertySource(
    properties =
        [
            "app.pseudonymize.generator=BUILDIN",
            "app.security.admin-user=admin",
            "app.security.admin-password={noop}very-secret",
        ],
)
@MockitoBean(types = [RequestService::class])
class StatisticsControllerTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var webClient: WebClient

    @BeforeEach
    fun setup(
        @Autowired mockMvc: MockMvc,
    ) {
        this.mockMvc = mockMvc
        this.webClient = MockMvcWebClientBuilder.mockMvcSetup(mockMvc).build()
    }

    @Test
    fun testShouldRequestStatisticsPage() {
        mockMvc.get("/statistics").andExpect {
            status { isOk() }
            view { name("statistics") }
        }
    }
}
