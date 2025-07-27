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

import de.ukw.ccc.bwhc.dto.*
import dev.dnpm.etl.processor.ArgProvider
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

@ExtendWith(MockitoExtension::class)
class BwhcBasedConsentEvaluatorTest {

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
        fun test(mtbFile: MtbFile, ttpConsentStatus: TtpConsentStatus, expectedConsentEvaluation: ConsentEvaluation) {
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
        fun test(mtbFile: MtbFile, expectedConsentEvaluation: ConsentEvaluation) {
            assertThat(consentEvaluator.check(mtbFile)).isEqualTo(expectedConsentEvaluation)
        }

    }

    // Util classes

    class WithGicsMtbFileProvider : ArgProvider(
        // Has file consent and broad consent => consent given
        Arguments.of(
            buildMtb(Consent.Status.ACTIVE),
            TtpConsentStatus.BROAD_CONSENT_GIVEN,
            ConsentEvaluation(TtpConsentStatus.BROAD_CONSENT_GIVEN, true)
        ),
        // Has file consent and broad consent missing => no consent given
        Arguments.of(
            buildMtb(Consent.Status.ACTIVE),
            TtpConsentStatus.BROAD_CONSENT_MISSING,
            ConsentEvaluation(TtpConsentStatus.BROAD_CONSENT_MISSING, false)
        ),
        // Has file consent and broad consent missing or rejected => no consent given
        Arguments.of(
            buildMtb(Consent.Status.ACTIVE),
            TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED,
            ConsentEvaluation(TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED, false)
        ),
        // Has file consent and no broad consent result => consent given
        Arguments.of(
            buildMtb(Consent.Status.ACTIVE),
            TtpConsentStatus.UNKNOWN_CHECK_FILE,
            ConsentEvaluation(TtpConsentStatus.UNKNOWN_CHECK_FILE, true)
        ),
        // Has file consent and failed to ask => no consent given
        Arguments.of(
            buildMtb(Consent.Status.ACTIVE),
            TtpConsentStatus.FAILED_TO_ASK,
            ConsentEvaluation(TtpConsentStatus.FAILED_TO_ASK, false)
        ),
        // File consent rejected and broad consent => consent given
        Arguments.of(
            buildMtb(Consent.Status.REJECTED),
            TtpConsentStatus.BROAD_CONSENT_GIVEN,
            ConsentEvaluation(TtpConsentStatus.BROAD_CONSENT_GIVEN, true)
        ),
        // File consent rejected and broad consent missing => no consent given
        Arguments.of(
            buildMtb(Consent.Status.REJECTED),
            TtpConsentStatus.BROAD_CONSENT_MISSING,
            ConsentEvaluation(TtpConsentStatus.BROAD_CONSENT_MISSING, false)
        ),
        // File consent rejected and broad consent missing or rejected => no consent given
        Arguments.of(
            buildMtb(Consent.Status.REJECTED),
            TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED,
            ConsentEvaluation(TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED, false)
        ),
        // File consent rejected and no broad consent result => no consent given
        Arguments.of(
            buildMtb(Consent.Status.REJECTED),
            TtpConsentStatus.UNKNOWN_CHECK_FILE,
            ConsentEvaluation(TtpConsentStatus.UNKNOWN_CHECK_FILE, false)
        ),
        // File consent rejected and failed to ask => no consent given
        Arguments.of(
            buildMtb(Consent.Status.REJECTED),
            TtpConsentStatus.FAILED_TO_ASK,
            ConsentEvaluation(TtpConsentStatus.FAILED_TO_ASK, false)
        )
    ) {

        companion object {
            fun buildMtb(consentStatus: Consent.Status): MtbFile {
                return MtbFile.builder().withPatient(
                    Patient.builder()
                        .withId("TEST_12345678")
                        .withBirthDate("2000-08-08")
                        .withGender(Patient.Gender.MALE)
                        .build()
                ).withConsent(
                    Consent.builder()
                        .withId("1")
                        .withStatus(consentStatus)
                        .withPatient("TEST_12345678")
                        .build()
                ).withEpisode(
                    Episode.builder()
                        .withId("1")
                        .withPatient("TEST_12345678")
                        .withPeriod(PeriodStart("2023-08-08"))
                        .build()
                ).build()
            }
        }
    }

    class MtbFileProvider : ArgProvider(
        // Has file consent => consent given
        Arguments.of(
            buildMtb(Consent.Status.ACTIVE),
            ConsentEvaluation(TtpConsentStatus.UNKNOWN_CHECK_FILE, true)
        ),
        // File consent rejected => no consent given
        Arguments.of(
            buildMtb(Consent.Status.REJECTED),
            ConsentEvaluation(TtpConsentStatus.UNKNOWN_CHECK_FILE, false)
        )
    ) {

        companion object {
            fun buildMtb(consentStatus: Consent.Status): MtbFile {
                return MtbFile.builder().withPatient(
                    Patient.builder()
                        .withId("TEST_12345678")
                        .withBirthDate("2000-08-08")
                        .withGender(Patient.Gender.MALE)
                        .build()
                ).withConsent(
                    Consent.builder()
                        .withId("1")
                        .withStatus(consentStatus)
                        .withPatient("TEST_12345678")
                        .build()
                ).withEpisode(
                    Episode.builder()
                        .withId("1")
                        .withPatient("TEST_12345678")
                        .withPeriod(PeriodStart("2023-08-08"))
                        .build()
                ).build()
            }
        }
    }

}
