/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2023  Comprehensive Cancer Center Mainfranken, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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

import de.ukw.ccc.bwhc.dto.*
import dev.dnpm.etl.processor.config.RestTargetProperties
import dev.dnpm.etl.processor.monitoring.RequestStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestTemplate

class RestMtbFileSenderTest {

    private lateinit var mockRestServiceServer: MockRestServiceServer

    private lateinit var restMtbFileSender: RestMtbFileSender

    @BeforeEach
    fun setup() {
        val restTemplate = RestTemplate()
        val restTargetProperties = RestTargetProperties("http://localhost:9000/mtbfile")

        this.mockRestServiceServer = MockRestServiceServer.createServer(restTemplate)

        this.restMtbFileSender = RestMtbFileSender(restTemplate, restTargetProperties)
    }

    @ParameterizedTest
    @MethodSource("deleteRequestWithResponseSource")
    fun shouldReturnExpectedResponseForDelete(requestWithResponse: RequestWithResponse) {
        this.mockRestServiceServer.expect {
            method(HttpMethod.DELETE)
            requestTo("/mtbfile")
        }.andRespond {
            withStatus(requestWithResponse.httpStatus).body(requestWithResponse.body).createResponse(it)
        }

        val response = restMtbFileSender.send(MtbFileSender.DeleteRequest("TestID", "PID"))
        assertThat(response.status).isEqualTo(requestWithResponse.response.status)
        assertThat(response.body).isEqualTo(requestWithResponse.response.body)
    }

    @ParameterizedTest
    @MethodSource("mtbFileRequestWithResponseSource")
    fun shouldReturnExpectedResponseForMtbFilePost(requestWithResponse: RequestWithResponse) {
        this.mockRestServiceServer.expect {
            method(HttpMethod.POST)
            requestTo("/mtbfile")
        }.andRespond {
            withStatus(requestWithResponse.httpStatus).body(requestWithResponse.body).createResponse(it)
        }

        val response = restMtbFileSender.send(MtbFileSender.MtbFileRequest("TestID", mtbFile))
        assertThat(response.status).isEqualTo(requestWithResponse.response.status)
        assertThat(response.body).isEqualTo(requestWithResponse.response.body)
    }

    companion object {
        data class RequestWithResponse(
            val httpStatus: HttpStatus,
            val body: String,
            val response: MtbFileSender.Response
        )

        private val warningBody = """
                {
                    "patient_id": "PID",
                    "issues": [
                        { "severity": "warning", "message": "Something is not right" }
                    ]
                }
            """.trimIndent()

        private val errorBody = """
                {
                    "patient_id": "PID",
                    "issues": [
                        { "severity": "error", "message": "Something is very bad" }
                    ]
                }
            """.trimIndent()

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
                RequestWithResponse(HttpStatus.OK, "{}", MtbFileSender.Response(RequestStatus.SUCCESS, "{}")),
                RequestWithResponse(
                    HttpStatus.CREATED,
                    warningBody,
                    MtbFileSender.Response(RequestStatus.WARNING, warningBody)
                ),
                RequestWithResponse(
                    HttpStatus.BAD_REQUEST,
                    "??",
                    MtbFileSender.Response(RequestStatus.ERROR, ERROR_RESPONSE_BODY)
                ),
                RequestWithResponse(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    errorBody,
                    MtbFileSender.Response(RequestStatus.ERROR, ERROR_RESPONSE_BODY)
                ),
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
    }


}