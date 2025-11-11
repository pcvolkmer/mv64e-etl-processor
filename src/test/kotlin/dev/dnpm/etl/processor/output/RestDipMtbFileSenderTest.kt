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
import dev.dnpm.etl.processor.CustomMediaType
import dev.dnpm.etl.processor.PatientPseudonym
import dev.dnpm.etl.processor.RequestId
import dev.dnpm.etl.processor.config.AppConfigProperties
import dev.dnpm.etl.processor.config.AppConfiguration
import dev.dnpm.etl.processor.config.RestTargetProperties
import dev.dnpm.etl.processor.monitoring.ReportService
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.pcvolkmer.mv64e.mtb.*
import java.time.Instant
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.retry.backoff.NoBackOffPolicy
import org.springframework.retry.policy.SimpleRetryPolicy
import org.springframework.retry.support.RetryTemplateBuilder
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestTemplate

class RestDipMtbFileSenderTest {

  @Nested
  inner class DnpmV2ContentRequest {

    private lateinit var mockRestServiceServer: MockRestServiceServer

    private lateinit var restMtbFileSender: RestMtbFileSender

    private var reportService =
        ReportService(ObjectMapper().registerModule(KotlinModule.Builder().build()))

    @BeforeEach
    fun setup() {
      val restTemplate = RestTemplate()
      val restTargetProperties = RestTargetProperties("http://localhost:9000/api", null, null)
      val retryTemplate = RetryTemplateBuilder().customPolicy(SimpleRetryPolicy(1)).build()

      this.mockRestServiceServer = MockRestServiceServer.createServer(restTemplate)

      this.restMtbFileSender =
          RestDipMtbFileSender(restTemplate, restTargetProperties, retryTemplate, reportService)
    }

    @ParameterizedTest
    @MethodSource(
        "dev.dnpm.etl.processor.output.RestDipMtbFileSenderTest#mtbFileRequestWithResponseSource"
    )
    fun shouldReturnExpectedResponseForDnpmV2MtbFilePost(requestWithResponse: RequestWithResponse) {
      this.mockRestServiceServer
          .expect(method(HttpMethod.POST))
          .andExpect(requestTo("http://localhost:9000/api/mtb/etl/patient-record"))
          .andExpect(
              header(
                  HttpHeaders.CONTENT_TYPE,
                  CustomMediaType.APPLICATION_VND_DNPM_V2_MTB_JSON_VALUE,
              )
          )
          .andRespond {
            withStatus(requestWithResponse.httpStatus)
                .body(requestWithResponse.body)
                .createResponse(it)
          }

      val response = restMtbFileSender.send(DnpmV2MtbFileRequest(TEST_REQUEST_ID, dnpmV2MtbFile()))
      assertThat(response.status).isEqualTo(requestWithResponse.response.status)
      assertThat(response.body).isEqualTo(requestWithResponse.response.body)
    }
  }

  @Nested
  inner class DeleteRequest {

    private lateinit var mockRestServiceServer: MockRestServiceServer

    private lateinit var restMtbFileSender: RestMtbFileSender

    private var reportService =
        ReportService(ObjectMapper().registerModule(KotlinModule.Builder().build()))

    @BeforeEach
    fun setup() {
      val restTemplate = RestTemplate()
      val restTargetProperties = RestTargetProperties("http://localhost:9000/api", null, null)
      val retryTemplate = RetryTemplateBuilder().customPolicy(SimpleRetryPolicy(1)).build()

      this.mockRestServiceServer = MockRestServiceServer.createServer(restTemplate)

      this.restMtbFileSender =
          RestDipMtbFileSender(restTemplate, restTargetProperties, retryTemplate, reportService)
    }

    @ParameterizedTest
    @MethodSource(
        "dev.dnpm.etl.processor.output.RestDipMtbFileSenderTest#deleteRequestWithResponseSource"
    )
    fun shouldReturnExpectedResponseForDelete(requestWithResponse: RequestWithResponse) {
      this.mockRestServiceServer
          .expect(method(HttpMethod.DELETE))
          .andExpect(
              requestTo("http://localhost:9000/api/mtb/etl/patient/${TEST_PATIENT_PSEUDONYM.value}")
          )
          .andRespond {
            withStatus(requestWithResponse.httpStatus)
                .body(requestWithResponse.body)
                .createResponse(it)
          }

      val response = restMtbFileSender.send(DeleteRequest(TEST_REQUEST_ID, TEST_PATIENT_PSEUDONYM))
      assertThat(response.status).isEqualTo(requestWithResponse.response.status)
      assertThat(response.body).isEqualTo(requestWithResponse.response.body)
    }

    @ParameterizedTest
    @MethodSource(
        "dev.dnpm.etl.processor.output.RestDipMtbFileSenderTest#deleteRequestWithResponseSource"
    )
    fun shouldRetryOnDeleteHttpRequestError(requestWithResponse: RequestWithResponse) {
      val restTemplate = RestTemplate()
      val restTargetProperties = RestTargetProperties("http://localhost:9000/api", null, null)
      val retryTemplate = AppConfiguration().retryTemplate(AppConfigProperties())
      retryTemplate.setBackOffPolicy(NoBackOffPolicy())

      this.mockRestServiceServer = MockRestServiceServer.createServer(restTemplate)
      this.restMtbFileSender =
          RestDipMtbFileSender(restTemplate, restTargetProperties, retryTemplate, reportService)

      val expectedCount =
          when (requestWithResponse.httpStatus) {
            // OK - No Retry
            HttpStatus.OK,
            HttpStatus.CREATED,
            HttpStatus.UNPROCESSABLE_ENTITY,
            HttpStatus.BAD_REQUEST -> ExpectedCount.max(1)
            // Request failed - Retry max 3 times
            else -> ExpectedCount.max(3)
          }

      this.mockRestServiceServer
          .expect(expectedCount, method(HttpMethod.DELETE))
          .andExpect(
              requestTo("http://localhost:9000/api/mtb/etl/patient/${TEST_PATIENT_PSEUDONYM.value}")
          )
          .andRespond {
            withStatus(requestWithResponse.httpStatus)
                .body(requestWithResponse.body)
                .createResponse(it)
          }

      val response = restMtbFileSender.send(DeleteRequest(TEST_REQUEST_ID, TEST_PATIENT_PSEUDONYM))
      assertThat(response.status).isEqualTo(requestWithResponse.response.status)
      assertThat(response.body).isEqualTo(requestWithResponse.response.body)
    }
  }

  companion object {
    data class RequestWithResponse(
        val httpStatus: HttpStatus,
        val body: String,
        val response: MtbFileSender.Response,
    )

    val TEST_REQUEST_ID = RequestId("TestId")
    val TEST_PATIENT_PSEUDONYM = PatientPseudonym("PID")

    fun dnpmV2MtbFile(): Mtb {
      return Mtb().apply {
        this.patient =
            dev.pcvolkmer.mv64e.mtb.Patient().apply {
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

    private const val ERROR_RESPONSE_BODY = "Sonstiger Fehler bei der Übertragung"

    /**
     * Synthetic http responses with related request status Also see:
     * https://ibmi-intra.cs.uni-tuebingen.de/display/ZPM/bwHC+REST+API
     */
    @JvmStatic
    fun mtbFileRequestWithResponseSource(): Set<RequestWithResponse> {
      return setOf(
          RequestWithResponse(
              HttpStatus.OK,
              responseBodyWithMaxSeverity(ReportService.Severity.INFO),
              MtbFileSender.Response(
                  RequestStatus.SUCCESS,
                  responseBodyWithMaxSeverity(ReportService.Severity.INFO),
              ),
          ),
          RequestWithResponse(
              HttpStatus.CREATED,
              responseBodyWithMaxSeverity(ReportService.Severity.WARNING),
              MtbFileSender.Response(
                  RequestStatus.WARNING,
                  responseBodyWithMaxSeverity(ReportService.Severity.WARNING),
              ),
          ),
          RequestWithResponse(
              HttpStatus.BAD_REQUEST,
              responseBodyWithMaxSeverity(ReportService.Severity.ERROR),
              MtbFileSender.Response(
                  RequestStatus.ERROR,
                  responseBodyWithMaxSeverity(ReportService.Severity.ERROR),
              ),
          ),
          RequestWithResponse(
              HttpStatus.UNPROCESSABLE_ENTITY,
              responseBodyWithMaxSeverity(ReportService.Severity.ERROR),
              MtbFileSender.Response(
                  RequestStatus.ERROR,
                  responseBodyWithMaxSeverity(ReportService.Severity.ERROR),
              ),
          ),
          // Some more errors not mentioned in documentation
          RequestWithResponse(
              HttpStatus.NOT_FOUND,
              ERROR_RESPONSE_BODY,
              MtbFileSender.Response(RequestStatus.ERROR, ERROR_RESPONSE_BODY),
          ),
          RequestWithResponse(
              HttpStatus.INTERNAL_SERVER_ERROR,
              ERROR_RESPONSE_BODY,
              MtbFileSender.Response(RequestStatus.ERROR, ERROR_RESPONSE_BODY),
          ),
      )
    }

    /**
     * Synthetic http responses with related request status Also see:
     * https://ibmi-intra.cs.uni-tuebingen.de/display/ZPM/bwHC+REST+API
     */
    @JvmStatic
    fun deleteRequestWithResponseSource(): Set<RequestWithResponse> {
      return setOf(
          RequestWithResponse(HttpStatus.OK, "", MtbFileSender.Response(RequestStatus.SUCCESS)),
          // Some more errors not mentioned in documentation
          RequestWithResponse(
              HttpStatus.NOT_FOUND,
              "what????",
              MtbFileSender.Response(RequestStatus.ERROR, ERROR_RESPONSE_BODY),
          ),
          RequestWithResponse(
              HttpStatus.INTERNAL_SERVER_ERROR,
              "what????",
              MtbFileSender.Response(RequestStatus.ERROR, ERROR_RESPONSE_BODY),
          ),
      )
    }

    fun responseBodyWithMaxSeverity(severity: ReportService.Severity): String {
      return when (severity) {
        ReportService.Severity.INFO ->
            """
                        {
                            "patient": "PID",
                            "issues": [
                                { "severity": "info", "message": "Info Message" }
                            ]
                        }
                    """

        ReportService.Severity.WARNING ->
            """
                        {
                            "patient": "PID",
                            "issues": [
                                { "severity": "info", "message": "Info Message" },
                                { "severity": "warning", "message": "Warning Message" }
                            ]
                        }
                    """

        ReportService.Severity.ERROR ->
            """
                        {
                            "patient": "PID",
                            "issues": [
                                { "severity": "info", "message": "Info Message" },
                                { "severity": "warning", "message": "Warning Message" },
                                { "severity": "error", "message": "Error Message" }
                            ]
                        }
                    """

        ReportService.Severity.FATAL ->
            """
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
