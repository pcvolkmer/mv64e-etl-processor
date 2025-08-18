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
import dev.dnpm.etl.processor.ArgProvider
import dev.dnpm.etl.processor.CustomMediaType
import dev.dnpm.etl.processor.consent.ConsentEvaluation
import dev.dnpm.etl.processor.consent.ConsentEvaluator
import dev.dnpm.etl.processor.consent.TtpConsentStatus
import dev.dnpm.etl.processor.services.RequestProcessor
import dev.pcvolkmer.mv64e.mtb.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsSource
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyValueClass
import org.mockito.kotlin.whenever
import org.springframework.core.io.ClassPathResource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.*

@ExtendWith(MockitoExtension::class)
class MtbFileRestControllerTest {

    private val objectMapper = ObjectMapper()

    @Nested
    inner class RequestsForDnpmDataModel21 {

        private lateinit var mockMvc: MockMvc

        private lateinit var requestProcessor: RequestProcessor
        private lateinit var consentEvaluator: ConsentEvaluator

        @BeforeEach
        fun setup(
            @Mock requestProcessor: RequestProcessor,
            @Mock consentEvaluator: ConsentEvaluator
        ) {
            this.requestProcessor = requestProcessor
            this.consentEvaluator = consentEvaluator
            val controller = MtbFileRestController(
                requestProcessor,
                consentEvaluator
            )
            this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
        }

        @Test
        fun shouldRespondPostRequest() {
            whenever(consentEvaluator.check(any())).thenReturn(
                ConsentEvaluation(
                    TtpConsentStatus.BROAD_CONSENT_GIVEN,
                    true
                )
            )

            val mtbFileContent =
                ClassPathResource("mv64e-mtb-fake-patient.json").inputStream.readAllBytes().toString(Charsets.UTF_8)

            mockMvc.post("/mtb") {
                content = mtbFileContent
                contentType = CustomMediaType.APPLICATION_VND_DNPM_V2_MTB_JSON
            }.andExpect {
                status {
                    isAccepted()
                }
            }

            verify(requestProcessor, times(1)).processMtbFile(any<Mtb>())
        }

        @ParameterizedTest
        @ArgumentsSource(Dnpm21MtbFile::class)
        fun shouldProcessPostRequest(mtb: Mtb, broadConsent: TtpConsentStatus, shouldProcess: String) {
            whenever(consentEvaluator.check(any<Mtb>())).thenReturn(
                ConsentEvaluation(
                    broadConsent,
                    shouldProcess == "process"
                )
            )

            mockMvc.post("/mtbfile") {
                content = objectMapper.writeValueAsString(mtb)
                contentType = CustomMediaType.APPLICATION_VND_DNPM_V2_MTB_JSON
            }.andExpect {
                status {
                    isAccepted()
                }
            }

            if (shouldProcess == "process") {
                verify(requestProcessor, times(1)).processMtbFile(any<Mtb>())
            } else {
                verify(requestProcessor, times(1)).processDeletion(
                    anyValueClass(),
                    org.mockito.kotlin.eq(broadConsent)
                )
            }
        }

        @Test
        fun shouldProcessDeleteRequest() {
            mockMvc.delete("/mtbfile/TEST_12345678").andExpect {
                status {
                    isAccepted()
                }
            }

            verify(requestProcessor, times(1)).processDeletion(
                anyValueClass(),
                org.mockito.kotlin.eq(TtpConsentStatus.UNKNOWN_CHECK_FILE)
            )
            verify(consentEvaluator, times(0)).check(any<Mtb>())
        }
    }
}

class Dnpm21MtbFile : ArgProvider(
    // No Metadata and no broad consent => delete
    Arguments.of(
        buildMtb(null),
        TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED,
        "delete"
    ),
    // No Metadata and broad consent given => process
    Arguments.of(
        buildMtb(null),
        TtpConsentStatus.BROAD_CONSENT_GIVEN,
        "process"
    ),
    // No model project consent and no broad consent => delete
    Arguments.of(
        buildMtb(MvhMetadata.builder().modelProjectConsent(ModelProjectConsent.builder().build()).build()),
        TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED,
        "delete"
    ),
    // No model project consent and broad consent given => process
    Arguments.of(
        buildMtb(MvhMetadata.builder().modelProjectConsent(ModelProjectConsent.builder().build()).build()),
        TtpConsentStatus.BROAD_CONSENT_GIVEN,
        "process"
    ),
    // Model project consent given and no broad consent => process
    Arguments.of(
        buildMtb(
            MvhMetadata.builder().modelProjectConsent(
                ModelProjectConsent.builder().provisions(
                    listOf(
                        Provision.builder().date(Date()).type(ConsentProvision.PERMIT)
                            .purpose(ModelProjectConsentPurpose.SEQUENCING).build()
                    )
                ).build()
            ).build()
        ),
        TtpConsentStatus.UNKNOWN_CHECK_FILE,
        "process"
    ),
    // Model project consent given and broad consent given => process
    Arguments.of(
        buildMtb(
            MvhMetadata.builder().modelProjectConsent(
                ModelProjectConsent.builder().provisions(
                    listOf(
                        Provision.builder().date(Date()).type(ConsentProvision.PERMIT)
                            .purpose(ModelProjectConsentPurpose.SEQUENCING).build()
                    )
                ).build()
            ).build()
        ),
        TtpConsentStatus.BROAD_CONSENT_GIVEN,
        "process"
    )
) {

    companion object {
        fun buildMtb(metadata: MvhMetadata?): Mtb {
            return Mtb.builder()
                .patient(
                    Patient.builder().id("TEST_12345678")
                        .birthDate(Date.from(Instant.parse("2000-08-08T12:34:56Z"))).gender(
                            GenderCoding.builder().code(GenderCodingCode.MALE).build()
                        ).build()
                )
                .metadata(metadata)
                .episodesOfCare(
                    listOf(
                        MtbEpisodeOfCare.builder().id("1")
                            .patient(Reference.builder().id("TEST_12345678").build())
                            .build()
                    )
                )
                .build()
        }
    }
}
