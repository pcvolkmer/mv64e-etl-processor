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

package dev.dnpm.etl.processor.consent

import dev.dnpm.etl.processor.ArgProvider
import dev.pcvolkmer.mv64e.mtb.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsSource
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.*

@ExtendWith(MockitoExtension::class)
class Dnpm21BasedConsentEvaluatorTest {

    @Nested
    inner class WithGicsConsentEnabled {

        lateinit var consentService: GicsConsentService
        lateinit var consentEvaluator: ConsentEvaluator

        @BeforeEach
        fun setUp(
            @Mock consentService: GicsConsentService
        ) {
            this.consentService = consentService
            this.consentEvaluator = ConsentEvaluator(consentService)
        }

        @ParameterizedTest
        @ArgumentsSource(WithGicsMtbFileProvider::class)
        fun test(mtbFile: Mtb, ttpConsentStatus: TtpConsentStatus, expectedConsentEvaluation: ConsentEvaluation) {
            whenever(consentService.getTtpBroadConsentStatus(anyString())).thenReturn(ttpConsentStatus)
            assertThat(consentEvaluator.check(mtbFile)).isEqualTo(expectedConsentEvaluation)
        }
    }

    @Nested
    inner class WithFileConsentOnly {

        lateinit var consentService: MtbFileConsentService
        lateinit var consentEvaluator: ConsentEvaluator

        @BeforeEach
        fun setUp() {
            this.consentService = MtbFileConsentService()
            this.consentEvaluator = ConsentEvaluator(consentService)
        }

        @ParameterizedTest
        @ArgumentsSource(MtbFileProvider::class)
        fun test(mtbFile: Mtb, expectedConsentEvaluation: ConsentEvaluation) {
            assertThat(consentEvaluator.check(mtbFile)).isEqualTo(expectedConsentEvaluation)
        }

    }

    // Util classes

    class WithGicsMtbFileProvider : ArgProvider(
        // Has file ModelProjectConsent and broad consent => consent given
        Arguments.of(
            buildMtb(ConsentProvision.PERMIT),
            TtpConsentStatus.BROAD_CONSENT_GIVEN,
            ConsentEvaluation(TtpConsentStatus.BROAD_CONSENT_GIVEN, true)
        ),
        // Has file ModelProjectConsent and broad consent missing => no consent given
        Arguments.of(
            buildMtb(ConsentProvision.PERMIT),
            TtpConsentStatus.BROAD_CONSENT_MISSING,
            ConsentEvaluation(TtpConsentStatus.BROAD_CONSENT_MISSING, false)
        ),
        // Has file ModelProjectConsent and broad consent missing or rejected => no consent given
        Arguments.of(
            buildMtb(ConsentProvision.PERMIT),
            TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED,
            ConsentEvaluation(TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED, false)
        ),
        // Has file ModelProjectConsent and MV consent => consent given
        Arguments.of(
            buildMtb(ConsentProvision.PERMIT),
            TtpConsentStatus.GENOM_DE_CONSENT_SEQUENCING_PERMIT,
            ConsentEvaluation(TtpConsentStatus.GENOM_DE_CONSENT_SEQUENCING_PERMIT, true)
        ),
        // Has file ModelProjectConsent and MV consent rejected => no consent given
        Arguments.of(
            buildMtb(ConsentProvision.PERMIT),
            TtpConsentStatus.GENOM_DE_SEQUENCING_REJECTED,
            ConsentEvaluation(TtpConsentStatus.GENOM_DE_SEQUENCING_REJECTED, false)
        ),
        // Has file ModelProjectConsent and MV consent missing => no consent given
        Arguments.of(
            buildMtb(ConsentProvision.PERMIT),
            TtpConsentStatus.GENOM_DE_CONSENT_MISSING,
            ConsentEvaluation(TtpConsentStatus.GENOM_DE_CONSENT_MISSING, false)
        ),
        // Has file ModelProjectConsent and no broad consent result => consent given
        Arguments.of(
            buildMtb(ConsentProvision.PERMIT),
            TtpConsentStatus.UNKNOWN_CHECK_FILE,
            ConsentEvaluation(TtpConsentStatus.UNKNOWN_CHECK_FILE, true)
        ),
        // Has file ModelProjectConsent and failed to ask => no consent given
        Arguments.of(
            buildMtb(ConsentProvision.PERMIT),
            TtpConsentStatus.FAILED_TO_ASK,
            ConsentEvaluation(TtpConsentStatus.FAILED_TO_ASK, false)
        ),
        // File ModelProjectConsent rejected and broad consent => consent given
        Arguments.of(
            buildMtb(ConsentProvision.DENY),
            TtpConsentStatus.BROAD_CONSENT_GIVEN,
            ConsentEvaluation(TtpConsentStatus.BROAD_CONSENT_GIVEN, true)
        ),
        // File ModelProjectConsent rejected and broad consent missing => no consent given
        Arguments.of(
            buildMtb(ConsentProvision.DENY),
            TtpConsentStatus.BROAD_CONSENT_MISSING,
            ConsentEvaluation(TtpConsentStatus.BROAD_CONSENT_MISSING, false)
        ),
        // File ModelProjectConsent rejected and broad consent missing or rejected => no consent given
        Arguments.of(
            buildMtb(ConsentProvision.DENY),
            TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED,
            ConsentEvaluation(TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED, false)
        ),
        // File ModelProjectConsent rejected and MV consent => consent given
        Arguments.of(
            buildMtb(ConsentProvision.DENY),
            TtpConsentStatus.GENOM_DE_CONSENT_SEQUENCING_PERMIT,
            ConsentEvaluation(TtpConsentStatus.GENOM_DE_CONSENT_SEQUENCING_PERMIT, true)
        ),
        // File ModelProjectConsent rejected and MV consent rejected => no consent given
        Arguments.of(
            buildMtb(ConsentProvision.DENY),
            TtpConsentStatus.GENOM_DE_SEQUENCING_REJECTED,
            ConsentEvaluation(TtpConsentStatus.GENOM_DE_SEQUENCING_REJECTED, false)
        ),
        // File ModelProjectConsent rejected and MV consent missing => no consent given
        Arguments.of(
            buildMtb(ConsentProvision.DENY),
            TtpConsentStatus.GENOM_DE_CONSENT_MISSING,
            ConsentEvaluation(TtpConsentStatus.GENOM_DE_CONSENT_MISSING, false)
        ),
        // File ModelProjectConsent rejected and no broad consent result => no consent given
        Arguments.of(
            buildMtb(ConsentProvision.DENY),
            TtpConsentStatus.UNKNOWN_CHECK_FILE,
            ConsentEvaluation(TtpConsentStatus.UNKNOWN_CHECK_FILE, false)
        ),
        // File ModelProjectConsent rejected and failed to ask => no consent given
        Arguments.of(
            buildMtb(ConsentProvision.DENY),
            TtpConsentStatus.FAILED_TO_ASK,
            ConsentEvaluation(TtpConsentStatus.FAILED_TO_ASK, false)
        )
    ) {

        companion object {
            fun buildMtb(consentProvision: ConsentProvision): Mtb {
                return Mtb.builder()
                    .patient(
                        Patient.builder().id("TEST_12345678")
                            .birthDate(Date.from(Instant.parse("2000-08-08T12:34:56Z"))).gender(
                                GenderCoding.builder().code(GenderCodingCode.MALE).build()
                            ).build()
                    )
                    .metadata(
                        MvhMetadata.builder().modelProjectConsent(
                            ModelProjectConsent.builder().provisions(
                                listOf(
                                    Provision.builder().date(Date()).type(consentProvision)
                                        .purpose(ModelProjectConsentPurpose.SEQUENCING).build()
                                )
                            ).build()
                        ).build()
                    )
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

    class MtbFileProvider : ArgProvider(
        // Has file consent => consent given
        Arguments.of(
            buildMtb(ConsentProvision.PERMIT),
            ConsentEvaluation(TtpConsentStatus.UNKNOWN_CHECK_FILE, true)
        ),
        // File consent rejected => no consent given
        Arguments.of(
            buildMtb(ConsentProvision.DENY),
            ConsentEvaluation(TtpConsentStatus.UNKNOWN_CHECK_FILE, false)
        )
    ) {

        companion object {
            fun buildMtb(consentProvision: ConsentProvision): Mtb {
                return Mtb.builder()
                    .patient(
                        Patient.builder().id("TEST_12345678")
                            .birthDate(Date.from(Instant.parse("2000-08-08T12:34:56Z"))).gender(
                                GenderCoding.builder().code(GenderCodingCode.MALE).build()
                            ).build()
                    )
                    .metadata(
                        MvhMetadata.builder().modelProjectConsent(
                            ModelProjectConsent.builder().provisions(
                                listOf(
                                    Provision.builder().date(Date()).type(consentProvision)
                                        .purpose(ModelProjectConsentPurpose.SEQUENCING).build()
                                )
                            ).build()
                        ).build()
                    )
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

}
