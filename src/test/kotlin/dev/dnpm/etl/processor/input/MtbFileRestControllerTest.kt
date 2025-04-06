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

package dev.dnpm.etl.processor.input

import com.fasterxml.jackson.databind.ObjectMapper
import de.ukw.ccc.bwhc.dto.*
import dev.dnpm.etl.processor.CustomMediaType
import dev.dnpm.etl.processor.services.RequestProcessor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyValueClass
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class MtbFileRestControllerTest {

    private val objectMapper = ObjectMapper()

    @Nested
    inner class BwhcRequests {

        private lateinit var mockMvc: MockMvc

        private lateinit var requestProcessor: RequestProcessor

        @BeforeEach
        fun setup(
            @Mock requestProcessor: RequestProcessor
        ) {
            this.requestProcessor = requestProcessor
            val controller = MtbFileRestController(requestProcessor)
            this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
        }

        @Test
        fun shouldProcessPostRequest() {
            mockMvc.post("/mtbfile") {
                content = objectMapper.writeValueAsString(bwhcMtbFileContent(Consent.Status.ACTIVE))
                contentType = MediaType.APPLICATION_JSON
            }.andExpect {
                status {
                    isAccepted()
                }
            }

            verify(requestProcessor, times(1)).processMtbFile(any())
        }

        @Test
        fun shouldProcessPostRequestWithRejectedConsent() {
            mockMvc.post("/mtbfile") {
                content = objectMapper.writeValueAsString(bwhcMtbFileContent(Consent.Status.REJECTED))
                contentType = MediaType.APPLICATION_JSON
            }.andExpect {
                status {
                    isAccepted()
                }
            }

            verify(requestProcessor, times(1)).processDeletion(anyValueClass())
        }

        @Test
        fun shouldProcessDeleteRequest() {
            mockMvc.delete("/mtbfile/TEST_12345678").andExpect {
                status {
                    isAccepted()
                }
            }

            verify(requestProcessor, times(1)).processDeletion(anyValueClass())
        }
    }

    @Nested
    inner class BwhcRequestsWithAlias {

        private lateinit var mockMvc: MockMvc

        private lateinit var requestProcessor: RequestProcessor

        @BeforeEach
        fun setup(
            @Mock requestProcessor: RequestProcessor
        ) {
            this.requestProcessor = requestProcessor
            val controller = MtbFileRestController(requestProcessor)
            this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
        }

        @Test
        fun shouldProcessPostRequest() {
            mockMvc.post("/mtb") {
                content = objectMapper.writeValueAsString(bwhcMtbFileContent(Consent.Status.ACTIVE))
                contentType = MediaType.APPLICATION_JSON
            }.andExpect {
                status {
                    isAccepted()
                }
            }

            verify(requestProcessor, times(1)).processMtbFile(any())
        }

        @Test
        fun shouldProcessPostRequestWithRejectedConsent() {
            mockMvc.post("/mtb") {
                content = objectMapper.writeValueAsString(bwhcMtbFileContent(Consent.Status.REJECTED))
                contentType = MediaType.APPLICATION_JSON
            }.andExpect {
                status {
                    isAccepted()
                }
            }

            verify(requestProcessor, times(1)).processDeletion(anyValueClass())
        }

        @Test
        fun shouldProcessDeleteRequest() {
            mockMvc.delete("/mtb/TEST_12345678").andExpect {
                status {
                    isAccepted()
                }
            }

            verify(requestProcessor, times(1)).processDeletion(anyValueClass())
        }
    }

    @Nested
    inner class RequestsForDnpmDataModel21 {

        private lateinit var mockMvc: MockMvc

        private lateinit var requestProcessor: RequestProcessor

        @BeforeEach
        fun setup(
            @Mock requestProcessor: RequestProcessor
        ) {
            this.requestProcessor = requestProcessor
            val controller = MtbFileRestController(requestProcessor)
            this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
        }

        @Test
        fun shouldRespondPostRequest() {
            val mtbFileContent = ClassPathResource("mv64e-mtb-fake-patient.json").inputStream.readAllBytes().toString(Charsets.UTF_8)

            mockMvc.post("/mtb") {
                content = mtbFileContent
                contentType = CustomMediaType.APPLICATION_VND_DNPM_V2_MTB_JSON
            }.andExpect {
                status {
                    isNotImplemented()
                }
            }

            verify(requestProcessor, times(0)).processMtbFile(any())
        }

    }

    companion object {
        fun bwhcMtbFileContent(consentStatus: Consent.Status) = MtbFile.builder()
            .withPatient(
                Patient.builder()
                    .withId("TEST_12345678")
                    .withBirthDate("2000-08-08")
                    .withGender(Patient.Gender.MALE)
                    .build()
            )
            .withConsent(
                Consent.builder()
                    .withId("1")
                    .withStatus(consentStatus)
                    .withPatient("TEST_12345678")
                    .build()
            )
            .withEpisode(
                Episode.builder()
                    .withId("1")
                    .withPatient("TEST_12345678")
                    .withPeriod(PeriodStart("2023-08-08"))
                    .build()
            )
            .build()
    }
}
