/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2023       Comprehensive Cancer Center Mainfranken
 * Copyright (c) 2023-2025  Paul-Christian Volkmer, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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

package dev.dnpm.etl.processor.output

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import dev.dnpm.etl.processor.RequestId
import dev.dnpm.etl.processor.config.AppConfiguration
import dev.dnpm.etl.processor.config.AppRestConfiguration
import dev.dnpm.etl.processor.config.AppSecurityConfiguration
import dev.dnpm.etl.processor.config.RestTargetProperties
import dev.dnpm.etl.processor.consent.ConsentEvaluator
import dev.dnpm.etl.processor.monitoring.ReportService
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.pcvolkmer.mv64e.mtb.*
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.CoreMatchers.not
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.retry.policy.SimpleRetryPolicy
import org.springframework.retry.support.RetryTemplateBuilder
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.*

@SpringBootTest
@MockitoBean(types = [ReportService::class])
@ContextConfiguration(
    classes =
        [
            AppConfiguration::class,
            AppSecurityConfiguration::class,
            AppRestConfiguration::class,
            ConsentEvaluator::class,
        ],
)
@TestPropertySource(
    properties = ["app.rest.uri=http://localhost:9000", "app.max-retry-attempts=5"],
)
class RestDipMtbFileSenderTest {

    @Nested
    inner class DnpmV2ContentRequest {

        private lateinit var mockRestServiceServer: MockRestServiceServer

        private lateinit var restMtbFileSender: RestMtbFileSender

        private var reportService =
            ReportService(ObjectMapper().registerModule(KotlinModule.Builder().build()))

        @BeforeEach
        fun setup(
            @Autowired restTemplate: RestTemplate
        ) {
            val restTemplate = restTemplate
            val restTargetProperties = RestTargetProperties("http://localhost:9000/api", null, null)
            val retryTemplate = RetryTemplateBuilder().customPolicy(SimpleRetryPolicy(1)).build()

            this.mockRestServiceServer = MockRestServiceServer.createServer(restTemplate)

            this.restMtbFileSender =
                RestDipMtbFileSender(restTemplate, restTargetProperties, retryTemplate, reportService)
        }

        @Test
        fun shouldNotSendJsonNullValues() {
            this.mockRestServiceServer
                .expect(method(HttpMethod.POST))
                .andExpect(requestTo("http://localhost:9000/api/mtb/etl/patient-record"))
                .andExpect(
                    content().string(not(containsString("null")))
                )
                .andRespond {
                    withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                            """
                        {
                            "patient": "PID",
                            "issues": [
                                { "severity": "info", "message": "Info Message" }
                            ]
                        }
                    """
                        )
                        .createResponse(it)
                }

            val response = restMtbFileSender.send(DnpmV2MtbFileRequest(RequestId("TEST1234"), dnpmV2MtbFile()))
            assertThat(response.status).isEqualTo(RequestStatus.SUCCESS)
        }
    }

    companion object {
        fun dnpmV2MtbFile(): Mtb {
            return Mtb().apply {
                this.patient =
                    Patient().apply {
                        this.id = "PID"
                        this.birthDate = Date.from(Instant.now())
                        this.gender = GenderCoding().apply { this.code = GenderCodingCode.MALE }
                    }
                this.episodesOfCare =
                    listOf(
                        MtbEpisodeOfCare().apply {
                            this.id = "1"
                            this.patient = Reference().apply { this.id = "PID" }
                            this.period = PeriodDate().apply { this.start = Date.from(Instant.now()) }
                        }
                    )
            }
        }
    }
}
