/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2025  Comprehensive Cancer Center Mainfranken, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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
import de.ukw.ccc.bwhc.dto.*
import dev.dnpm.etl.processor.PatientPseudonym
import dev.dnpm.etl.processor.RequestId
import dev.dnpm.etl.processor.config.RestTargetProperties
import dev.dnpm.etl.processor.monitoring.ReportService
import dev.dnpm.etl.processor.monitoring.RequestStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.retry.policy.SimpleRetryPolicy
import org.springframework.retry.support.RetryTemplateBuilder
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestTemplate

class RestBwhcMtbFileSenderTest {

    private lateinit var mockRestServiceServer: MockRestServiceServer

    private lateinit var restMtbFileSender: RestMtbFileSender

    private var reportService = ReportService(ObjectMapper().registerModule(KotlinModule.Builder().build()))

    @BeforeEach
    fun setup() {
        val restTemplate = RestTemplate()
        val restTargetProperties = RestTargetProperties("http://localhost:9000/mtbfile", null, null)
        val retryTemplate = RetryTemplateBuilder().customPolicy(SimpleRetryPolicy(1)).build()

        this.mockRestServiceServer = MockRestServiceServer.createServer(restTemplate)

        this.restMtbFileSender =
            RestBwhcMtbFileSender(restTemplate, restTargetProperties, retryTemplate, reportService)
    }

    @ParameterizedTest
    @MethodSource("deleteRequestWithResponseSource")
    fun shouldReturnExpectedResponseForDelete(requestWithResponse: RequestWithResponse) {
        this.mockRestServiceServer
            .expect(method(HttpMethod.DELETE))
            .andExpect(requestTo("http://localhost:9000/mtbfile/Patient/${TEST_PATIENT_PSEUDONYM.value}"))
            .andRespond {
                withStatus(requestWithResponse.httpStatus).body(requestWithResponse.body).createResponse(it)
            }

        val response = restMtbFileSender.send(MtbFileSender.DeleteRequest(TEST_REQUEST_ID, TEST_PATIENT_PSEUDONYM))
        assertThat(response.status).isEqualTo(requestWithResponse.response.status)
        assertThat(response.body).isEqualTo(requestWithResponse.response.body)
    }

    @ParameterizedTest
    @MethodSource("mtbFileRequestWithResponseSource")
    fun shouldReturnExpectedResponseForMtbFilePost(requestWithResponse: RequestWithResponse) {
        this.mockRestServiceServer
            .expect(method(HttpMethod.POST))
            .andExpect(requestTo("http://localhost:9000/mtbfile/MTBFile"))
            .andRespond {
                withStatus(requestWithResponse.httpStatus).body(requestWithResponse.body).createResponse(it)
            }

        val response = restMtbFileSender.send(MtbFileSender.MtbFileRequest(TEST_REQUEST_ID, mtbFile))
        assertThat(response.status).isEqualTo(requestWithResponse.response.status)
        assertThat(response.body).isEqualTo(requestWithResponse.response.body)
    }

    @ParameterizedTest
    @MethodSource("mtbFileRequestWithResponseSource")
    fun shouldRetryOnMtbFileHttpRequestError(requestWithResponse: RequestWithResponse) {
        val restTemplate = RestTemplate()
        val restTargetProperties = RestTargetProperties("http://localhost:9000/mtbfile", null, null)
        val retryTemplate = RetryTemplateBuilder().customPolicy(SimpleRetryPolicy(3)).build()

        this.mockRestServiceServer = MockRestServiceServer.createServer(restTemplate)
        this.restMtbFileSender =
            RestBwhcMtbFileSender(restTemplate, restTargetProperties, retryTemplate, reportService)

        val expectedCount = when (requestWithResponse.httpStatus) {
            // OK - No Retry
            HttpStatus.OK, HttpStatus.CREATED -> ExpectedCount.max(1)
            // Request failed - Retry max 3 times
            else -> ExpectedCount.max(3)
        }

        this.mockRestServiceServer
            .expect(expectedCount, method(HttpMethod.POST))
            .andExpect(requestTo("http://localhost:9000/mtbfile/MTBFile"))
            .andRespond {
                withStatus(requestWithResponse.httpStatus).body(requestWithResponse.body).createResponse(it)
            }

        val response = restMtbFileSender.send(MtbFileSender.MtbFileRequest(TEST_REQUEST_ID, mtbFile))
        assertThat(response.status).isEqualTo(requestWithResponse.response.status)
        assertThat(response.body).isEqualTo(requestWithResponse.response.body)
    }

    @ParameterizedTest
    @MethodSource("deleteRequestWithResponseSource")
    fun shouldRetryOnDeleteHttpRequestError(requestWithResponse: RequestWithResponse) {
        val restTemplate = RestTemplate()
        val restTargetProperties = RestTargetProperties("http://localhost:9000/mtbfile", null, null)
        val retryTemplate = RetryTemplateBuilder().customPolicy(SimpleRetryPolicy(3)).build()

        this.mockRestServiceServer = MockRestServiceServer.createServer(restTemplate)
        this.restMtbFileSender =
            RestBwhcMtbFileSender(restTemplate, restTargetProperties, retryTemplate, reportService)

        val expectedCount = when (requestWithResponse.httpStatus) {
            // OK - No Retry
            HttpStatus.OK, HttpStatus.CREATED -> ExpectedCount.max(1)
            // Request failed - Retry max 3 times
            else -> ExpectedCount.max(3)
        }

        this.mockRestServiceServer
            .expect(expectedCount, method(HttpMethod.DELETE))
            .andExpect(requestTo("http://localhost:9000/mtbfile/Patient/${TEST_PATIENT_PSEUDONYM.value}"))
            .andRespond {
                withStatus(requestWithResponse.httpStatus).body(requestWithResponse.body).createResponse(it)
            }

        val response = restMtbFileSender.send(MtbFileSender.DeleteRequest(TEST_REQUEST_ID, TEST_PATIENT_PSEUDONYM))
        assertThat(response.status).isEqualTo(requestWithResponse.response.status)
        assertThat(response.body).isEqualTo(requestWithResponse.response.body)
    }

    companion object {
        data class RequestWithResponse(
            val httpStatus: HttpStatus,
            val body: String,
            val response: MtbFileSender.Response
        )

        val TEST_REQUEST_ID = RequestId("TestId")
        val TEST_PATIENT_PSEUDONYM = PatientPseudonym("PID")

        val mtbFile: MtbFile = MtbFile.builder()
            .withPatient(
                Patient.builder()
                    .withId("PID")
                    .withBirthDate("2000-08-08")
                    .withGender(Patient.Gender.MALE)
                    .build()
            )
            .withConsent(
                Consent.builder()
                    .withId("1")
                    .withStatus(Consent.Status.ACTIVE)
                    .withPatient("PID")
                    .build()
            )
            .withEpisode(
                Episode.builder()
                    .withId("1")
                    .withPatient("PID")
                    .withPeriod(PeriodStart("2023-08-08"))
                    .build()
            )
            .build()

        private const val ERROR_RESPONSE_BODY = "Sonstiger Fehler bei der Übertragung"

        /**
         * Synthetic http responses with related request status
         * Also see: https://ibmi-intra.cs.uni-tuebingen.de/display/ZPM/bwHC+REST+API
         */
        @JvmStatic
        fun mtbFileRequestWithResponseSource(): Set<RequestWithResponse> {
            return setOf(
                RequestWithResponse(
                    HttpStatus.OK,
                    responseBodyWithMaxSeverity(ReportService.Severity.INFO),
                    MtbFileSender.Response(
                        RequestStatus.SUCCESS,
                        responseBodyWithMaxSeverity(ReportService.Severity.INFO)
                    )
                ),
                RequestWithResponse(
                    HttpStatus.CREATED,
                    responseBodyWithMaxSeverity(ReportService.Severity.WARNING),
                    MtbFileSender.Response(
                        RequestStatus.WARNING,
                        responseBodyWithMaxSeverity(ReportService.Severity.WARNING)
                    )
                ),
                RequestWithResponse(
                    HttpStatus.BAD_REQUEST,
                    responseBodyWithMaxSeverity(ReportService.Severity.ERROR),
                    MtbFileSender.Response(RequestStatus.ERROR, responseBodyWithMaxSeverity(ReportService.Severity.ERROR))
                ),
                RequestWithResponse(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    responseBodyWithMaxSeverity(ReportService.Severity.FATAL),
                    MtbFileSender.Response(
                        RequestStatus.ERROR,
                        responseBodyWithMaxSeverity(ReportService.Severity.FATAL)
                    )
                ),
                // Some more errors not mentioned in documentation
                RequestWithResponse(
                    HttpStatus.NOT_FOUND,
                    ERROR_RESPONSE_BODY,
                    MtbFileSender.Response(RequestStatus.ERROR, ERROR_RESPONSE_BODY)
                ),
                RequestWithResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ERROR_RESPONSE_BODY,
                    MtbFileSender.Response(RequestStatus.ERROR, ERROR_RESPONSE_BODY)
                )
            )
        }

        /**
         * Synthetic http responses with related request status
         * Also see: https://ibmi-intra.cs.uni-tuebingen.de/display/ZPM/bwHC+REST+API
         */
        @JvmStatic
        fun deleteRequestWithResponseSource(): Set<RequestWithResponse> {
            return setOf(
                RequestWithResponse(HttpStatus.OK, "", MtbFileSender.Response(RequestStatus.SUCCESS)),
                // Some more errors not mentioned in documentation
                RequestWithResponse(
                    HttpStatus.NOT_FOUND,
                    "what????",
                    MtbFileSender.Response(RequestStatus.ERROR, ERROR_RESPONSE_BODY)
                ),
                RequestWithResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "what????",
                    MtbFileSender.Response(RequestStatus.ERROR, ERROR_RESPONSE_BODY)
                )
            )
        }

        fun responseBodyWithMaxSeverity(severity: ReportService.Severity): String {
            return when (severity) {
                ReportService.Severity.INFO -> """
                        {
                            "patient": "PID",
                            "issues": [
                                { "severity": "info", "message": "Info Message" }
                            ]
                        }
                    """

                ReportService.Severity.WARNING -> """
                        {
                            "patient": "PID",
                            "issues": [
                                { "severity": "info", "message": "Info Message" },
                                { "severity": "warning", "message": "Warning Message" }
                            ]
                        }
                    """

                ReportService.Severity.ERROR -> """
                        {
                            "patient": "PID",
                            "issues": [
                                { "severity": "info", "message": "Info Message" },
                                { "severity": "warning", "message": "Warning Message" },
                                { "severity": "error", "message": "Error Message" }
                            ]
                        }
                    """

                ReportService.Severity.FATAL -> """
                        {
                            "patient": "PID",
                            "issues": [
                                { "severity": "info", "message": "Info Message" },
                                { "severity": "warning", "message": "Warning Message" },
                                { "severity": "error", "message": "Error Message" },
                                { "severity": "fatal", "message": "Fatal Message" }
                            ]
                        }
                    """
            }
        }
    }


}