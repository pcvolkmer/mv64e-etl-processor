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

package dev.dnpm.etl.processor

import com.fasterxml.jackson.databind.ObjectMapper
import de.ukw.ccc.bwhc.dto.*
import dev.dnpm.etl.processor.monitoring.RequestRepository
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.dnpm.etl.processor.output.BwhcV1MtbFileRequest
import dev.dnpm.etl.processor.output.MtbFileSender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@ExtendWith(SpringExtension::class)
@SpringBootTest
@MockitoBean(types = [MtbFileSender::class])
@TestPropertySource(
    properties = [
        "app.rest.uri=http://example.com",
        "app.pseudonymize.generator=buildin",
        "app.consent.service=buildin"
    ]
)
class EtlProcessorApplicationTests : AbstractTestcontainerTest() {

    @Test
    fun contextLoadsIfMtbFileSenderConfigured(@Autowired context: ApplicationContext) {
        // Simply check bean configuration
        assertThat(context).isNotNull
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
    @AutoConfigureMockMvc
    @TestPropertySource(
        properties = [
            "app.pseudonymize.generator=buildin",
            "app.consent.service=buildin",
            "app.transformations[0].path=diagnoses[*].icd10.version",
            "app.transformations[0].from=2013",
            "app.transformations[0].to=2014",
        ]
    )
    inner class TransformationTest {

        @MockitoBean
        private lateinit var mtbFileSender: MtbFileSender

        @Autowired
        private lateinit var mockMvc: MockMvc

        @Autowired
        private lateinit var objectMapper: ObjectMapper

        @BeforeEach
        fun setup(@Autowired requestRepository: RequestRepository) {
            requestRepository.deleteAll()
        }

        @Test
        fun mtbFileIsTransformed() {
            doAnswer {
                MtbFileSender.Response(RequestStatus.SUCCESS)
            }.whenever(mtbFileSender).send(any<BwhcV1MtbFileRequest>())

            val mtbFile = MtbFile.builder()
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
                        .withStatus(Consent.Status.ACTIVE)
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
                .withDiagnoses(
                    listOf(
                        Diagnosis.builder()
                            .withId("1234")
                            .withIcd10(Icd10.builder().withCode("F79.9").withVersion("2013").build())
                            .build()
                    )
                )
                .build()

            mockMvc.post("/mtbfile") {
                content = objectMapper.writeValueAsString(mtbFile)
                contentType = MediaType.APPLICATION_JSON
            }.andExpect {
                status {
                    isAccepted()
                }
            }

            val captor = argumentCaptor<BwhcV1MtbFileRequest>()
            verify(mtbFileSender).send(captor.capture())
            assertThat(captor.firstValue.content.diagnoses).hasSize(1).allMatch { diagnosis ->
                diagnosis.icd10.version == "2014"
            }
        }
    }

}
