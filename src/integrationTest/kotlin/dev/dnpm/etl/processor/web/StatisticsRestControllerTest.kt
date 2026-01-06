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

import dev.dnpm.etl.processor.Fingerprint
import dev.dnpm.etl.processor.PatientId
import dev.dnpm.etl.processor.PatientPseudonym
import dev.dnpm.etl.processor.config.AppConfiguration
import dev.dnpm.etl.processor.config.AppSecurityConfiguration
import dev.dnpm.etl.processor.monitoring.CountedState
import dev.dnpm.etl.processor.monitoring.Request
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.dnpm.etl.processor.monitoring.RequestType
import dev.dnpm.etl.processor.monitoring.SubmissionType
import dev.dnpm.etl.processor.randomRequestId
import dev.dnpm.etl.processor.services.RequestService
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType.TEXT_EVENT_STREAM
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.client.MockMvcWebTestClient
import org.springframework.test.web.servlet.get
import org.springframework.web.context.WebApplicationContext
import reactor.core.publisher.Sinks
import reactor.test.StepVerifier
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@WebMvcTest(controllers = [StatisticsRestController::class])
@ExtendWith(value = [MockitoExtension::class, SpringExtension::class])
@ContextConfiguration(
    classes =
        [StatisticsRestController::class, AppConfiguration::class, AppSecurityConfiguration::class],
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
class StatisticsRestControllerTest {
    private lateinit var mockMvc: MockMvc

    private lateinit var statisticsUpdateProducer: Sinks.Many<Any>
    private lateinit var requestService: RequestService

    @BeforeEach
    fun setup(
        @Autowired mockMvc: MockMvc,
        @Autowired statisticsUpdateProducer: Sinks.Many<Any>,
        @Autowired requestService: RequestService,
    ) {
        this.mockMvc = mockMvc
        this.statisticsUpdateProducer = statisticsUpdateProducer
        this.requestService = requestService
    }

    @Nested
    inner class RequestStatesTest {
        @Test
        fun testShouldRequestStatesForMtbFiles() {
            doAnswer { _ ->
                listOf(CountedState(42, RequestStatus.WARNING), CountedState(1, RequestStatus.UNKNOWN))
            }.whenever(requestService)
                .countStates()

            mockMvc.get("/statistics/requeststates").andExpect {
                status { isOk() }
                    .also {
                        jsonPath("$", hasSize<Int>(2))
                        jsonPath("$[0].name", equalTo(RequestStatus.WARNING.name))
                        jsonPath("$[0].value", equalTo(42))
                        jsonPath("$[1].name", equalTo(RequestStatus.UNKNOWN.name))
                        jsonPath("$[1].value", equalTo(1))
                    }
            }
        }

        @Test
        fun testShouldRequestStatesForDeletes() {
            doAnswer { _ ->
                listOf(CountedState(42, RequestStatus.SUCCESS), CountedState(1, RequestStatus.ERROR))
            }.whenever(requestService)
                .countDeleteStates()

            mockMvc.get("/statistics/requeststates?delete=true").andExpect {
                status { isOk() }
                    .also {
                        jsonPath("$", hasSize<Int>(2))
                        jsonPath("$[0].name", equalTo(RequestStatus.SUCCESS.name))
                        jsonPath("$[0].value", equalTo(42))
                        jsonPath("$[1].name", equalTo(RequestStatus.ERROR.name))
                        jsonPath("$[1].value", equalTo(1))
                    }
            }
        }
    }

    @Nested
    inner class PatientRequestStatesTest {
        @Test
        fun testShouldRequestPatientStatesForMtbFiles() {
            doAnswer { _ ->
                listOf(CountedState(42, RequestStatus.WARNING), CountedState(1, RequestStatus.UNKNOWN))
            }.whenever(requestService)
                .findPatientUniqueStates()

            mockMvc.get("/statistics/requestpatientstates").andExpect {
                status { isOk() }
                    .also {
                        jsonPath("$", hasSize<Int>(2))
                        jsonPath("$[0].name", equalTo(RequestStatus.WARNING.name))
                        jsonPath("$[0].value", equalTo(42))
                        jsonPath("$[1].name", equalTo(RequestStatus.UNKNOWN.name))
                        jsonPath("$[1].value", equalTo(1))
                    }
            }
        }

        @Test
        fun testShouldRequestPatientStatesForDeletes() {
            doAnswer { _ ->
                listOf(CountedState(42, RequestStatus.SUCCESS), CountedState(1, RequestStatus.ERROR))
            }.whenever(requestService)
                .findPatientUniqueDeleteStates()

            mockMvc.get("/statistics/requestpatientstates?delete=true").andExpect {
                status { isOk() }
                    .also {
                        jsonPath("$", hasSize<Int>(2))
                        jsonPath("$[0].name", equalTo(RequestStatus.SUCCESS.name))
                        jsonPath("$[0].value", equalTo(42))
                        jsonPath("$[1].name", equalTo(RequestStatus.ERROR.name))
                        jsonPath("$[1].value", equalTo(1))
                    }
            }
        }
    }

    @Nested
    inner class LastMonthStatesTest {
        @BeforeEach
        fun setup() {
            val zoneId = ZoneId.of("Europe/Berlin")
            doAnswer { _ ->
                listOf(
                    Request(
                        1,
                        randomRequestId(),
                        PatientPseudonym("TEST_12345678901"),
                        PatientId("P1"),
                        Fingerprint("0123456789abcdef1"),
                        RequestType.MTB_FILE,
                        SubmissionType.TEST,
                        RequestStatus.SUCCESS,
                        Instant
                            .now()
                            .atZone(zoneId)
                            .truncatedTo(ChronoUnit.DAYS)
                            .minus(2, ChronoUnit.DAYS)
                            .toInstant(),
                    ),
                    Request(
                        2,
                        randomRequestId(),
                        PatientPseudonym("TEST_12345678902"),
                        PatientId("P2"),
                        Fingerprint("0123456789abcdef2"),
                        RequestType.MTB_FILE,
                        SubmissionType.TEST,
                        RequestStatus.WARNING,
                        Instant
                            .now()
                            .atZone(zoneId)
                            .truncatedTo(ChronoUnit.DAYS)
                            .minus(2, ChronoUnit.DAYS)
                            .toInstant(),
                    ),
                    Request(
                        3,
                        randomRequestId(),
                        PatientPseudonym("TEST_12345678901"),
                        PatientId("P2"),
                        Fingerprint("0123456789abcdee1"),
                        RequestType.DELETE,
                        SubmissionType.TEST,
                        RequestStatus.ERROR,
                        Instant
                            .now()
                            .atZone(zoneId)
                            .truncatedTo(ChronoUnit.DAYS)
                            .minus(1, ChronoUnit.DAYS)
                            .toInstant(),
                    ),
                    Request(
                        4,
                        randomRequestId(),
                        PatientPseudonym("TEST_12345678902"),
                        PatientId("P2"),
                        Fingerprint("0123456789abcdef2"),
                        RequestType.MTB_FILE,
                        SubmissionType.TEST,
                        RequestStatus.DUPLICATION,
                        Instant
                            .now()
                            .atZone(zoneId)
                            .truncatedTo(ChronoUnit.DAYS)
                            .minus(1, ChronoUnit.DAYS)
                            .toInstant(),
                    ),
                    Request(
                        5,
                        randomRequestId(),
                        PatientPseudonym("TEST_12345678902"),
                        PatientId("P2"),
                        Fingerprint("0123456789abcdef2"),
                        RequestType.DELETE,
                        SubmissionType.TEST,
                        RequestStatus.UNKNOWN,
                        Instant
                            .now()
                            .atZone(zoneId)
                            .truncatedTo(ChronoUnit.DAYS)
                            .toInstant(),
                    ),
                )
            }.whenever(requestService)
                .findAll()
        }

        @Test
        fun testShouldRequestLastMonthForMtbFiles() {
            mockMvc.get("/statistics/requestslastmonth").andExpect {
                status { isOk() }
                    .also { jsonPath("$", hasSize<Int>(31)) }
                    .also {
                        jsonPath("$[28].nameValues.error", equalTo(0))
                        jsonPath("$[28].nameValues.warning", equalTo(1))
                        jsonPath("$[28].nameValues.success", equalTo(1))
                        jsonPath("$[28].nameValues.duplication", equalTo(0))
                        jsonPath("$[28].nameValues.unknown", equalTo(0))
                        jsonPath("$[29].nameValues.error", equalTo(0))
                        jsonPath("$[29].nameValues.warning", equalTo(0))
                        jsonPath("$[29].nameValues.success", equalTo(0))
                        jsonPath("$[29].nameValues.duplication", equalTo(1))
                        jsonPath("$[29].nameValues.unknown", equalTo(0))
                    }
            }
        }

        @Test
        fun testShouldRequestLastMonthForDeletes() {
            mockMvc.get("/statistics/requestslastmonth?delete=true").andExpect {
                status { isOk() }
                    .also { jsonPath("$", hasSize<Int>(31)) }
                    .also {
                        jsonPath("$[29].nameValues.error", equalTo(1))
                        jsonPath("$[29].nameValues.warning", equalTo(0))
                        jsonPath("$[29].nameValues.success", equalTo(0))
                        jsonPath("$[29].nameValues.duplication", equalTo(0))
                        jsonPath("$[29].nameValues.unknown", equalTo(0))
                        jsonPath("$[30].nameValues.error", equalTo(0))
                        jsonPath("$[30].nameValues.warning", equalTo(0))
                        jsonPath("$[30].nameValues.success", equalTo(0))
                        jsonPath("$[30].nameValues.duplication", equalTo(0))
                        jsonPath("$[30].nameValues.unknown", equalTo(1))
                    }
            }
        }
    }

    @Nested
    inner class SseTest {
        private lateinit var webClient: WebTestClient

        @BeforeEach
        fun setup(applicationContext: WebApplicationContext) {
            this.webClient = MockMvcWebTestClient.bindToApplicationContext(applicationContext).build()
        }

        @Test
        fun testShouldRequestSSE() {
            statisticsUpdateProducer.emitComplete { _, _ -> true }

            val result =
                webClient
                    .get()
                    .uri("http://localhost/statistics/events")
                    .accept(TEXT_EVENT_STREAM)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectHeader()
                    .contentType(TEXT_EVENT_STREAM)
                    .returnResult(String::class.java)

            StepVerifier.create(result.responseBody).expectComplete().verify()
        }
    }
}
