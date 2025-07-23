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

package dev.dnpm.etl.processor.pseudonym

import com.fasterxml.jackson.databind.ObjectMapper
import de.ukw.ccc.bwhc.dto.*
import de.ukw.ccc.bwhc.dto.Patient
import dev.pcvolkmer.mv64e.mtb.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.anyValueClass
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import org.springframework.core.io.ClassPathResource
import java.time.Instant
import java.util.*

@ExtendWith(MockitoExtension::class)
class ExtensionsTest {

    @Nested
    inner class UsingBwhcDatamodel {

        val FAKE_MTB_FILE_PATH = "fake_MTBFile.json"
        val CLEAN_PATIENT_ID = "5dad2f0b-49c6-47d8-a952-7b9e9e0f7549"

        private fun fakeMtbFile(): MtbFile {
            val mtbFile = ClassPathResource(FAKE_MTB_FILE_PATH).inputStream
            return ObjectMapper().readValue(mtbFile, MtbFile::class.java)
        }

        private fun MtbFile.serialized(): String {
            return ObjectMapper().writeValueAsString(this)
        }

        @Test
        fun shouldNotContainCleanPatientId(@Mock pseudonymizeService: PseudonymizeService) {
            doAnswer {
                it.arguments[0]
                "PSEUDO-ID"
            }.whenever(pseudonymizeService).patientPseudonym(anyValueClass())

            val mtbFile = fakeMtbFile()

            mtbFile.pseudonymizeWith(pseudonymizeService)

            assertThat(mtbFile.patient.id).isEqualTo("PSEUDO-ID")
            assertThat(mtbFile.serialized()).doesNotContain(CLEAN_PATIENT_ID)
        }

        @Test
        fun shouldNotContainAnyUuidAfterRehashingOfIds(@Mock pseudonymizeService: PseudonymizeService) {
            doAnswer {
                it.arguments[0]
                "PSEUDO-ID"
            }.whenever(pseudonymizeService).patientPseudonym(anyValueClass())

            doAnswer {
                "TESTDOMAIN"
            }.whenever(pseudonymizeService).prefix()

            val mtbFile = fakeMtbFile()

            mtbFile.pseudonymizeWith(pseudonymizeService)
            mtbFile.anonymizeContentWith(pseudonymizeService)

            val pattern =
                "\"[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\"".toRegex()
                    .toPattern()
            val matcher = pattern.matcher(mtbFile.serialized())

            assertThrows<IllegalStateException> {
                matcher.find()
                matcher.group()
            }.also {
                assertThat(it.message).isEqualTo("No match found")
            }

        }

        @Test
        fun shouldRehashIdsWithPrefix(@Mock pseudonymizeService: PseudonymizeService) {
            doAnswer {
                it.arguments[0]
                "PSEUDO-ID"
            }.whenever(pseudonymizeService).patientPseudonym(anyValueClass())

            doAnswer {
                "TESTDOMAIN"
            }.whenever(pseudonymizeService).prefix()

            val mtbFile = MtbFile.builder()
                .withPatient(
                    Patient.builder()
                        .withId("1")
                        .withBirthDate("2000-08-08")
                        .withGender(Patient.Gender.MALE)
                        .build()
                )
                .withConsent(
                    Consent.builder()
                        .withId("1")
                        .withStatus(Consent.Status.ACTIVE)
                        .withPatient("123")
                        .build()
                )
                .withEpisode(
                    Episode.builder()
                        .withId("1")
                        .withPatient("1")
                        .withPeriod(PeriodStart("2023-08-08"))
                        .build()
                )
                .build()

            mtbFile.pseudonymizeWith(pseudonymizeService)
            mtbFile.anonymizeContentWith(pseudonymizeService)


            assertThat(mtbFile.episode.id)
                // TESTDOMAIN<sha256(TESTDOMAIN-1)[0-41]>
                .isEqualTo("TESTDOMAIN44e20a53bbbf9f3ae39626d05df7014dcd77d6098")
        }

        @Test
        fun shouldNotThrowExceptionOnNullValues(@Mock pseudonymizeService: PseudonymizeService) {
            doAnswer {
                it.arguments[0]
                "PSEUDO-ID"
            }.whenever(pseudonymizeService).patientPseudonym(anyValueClass())

            doAnswer {
                "TESTDOMAIN"
            }.whenever(pseudonymizeService).prefix()

            val mtbFile = MtbFile.builder()
                .withPatient(
                    Patient.builder()
                        .withId("1")
                        .withBirthDate("2000-08-08")
                        .withGender(Patient.Gender.MALE)
                        .build()
                )
                .withConsent(
                    Consent.builder()
                        .withId("1")
                        .withStatus(Consent.Status.ACTIVE)
                        .withPatient("123")
                        .build()
                )
                .withEpisode(
                    Episode.builder()
                        .withId("1")
                        .withPatient("1")
                        .withPeriod(PeriodStart("2023-08-08"))
                        .build()
                )
                .withClaims(null)
                .withDiagnoses(null)
                .withCarePlans(null)
                .withClaimResponses(null)
                .withEcogStatus(null)
                .withFamilyMemberDiagnoses(null)
                .withGeneticCounsellingRequests(null)
                .withHistologyReevaluationRequests(null)
                .withHistologyReports(null)
                .withLastGuidelineTherapies(null)
                .withMolecularPathologyFindings(null)
                .withMolecularTherapies(null)
                .withNgsReports(null)
                .withPreviousGuidelineTherapies(null)
                .withRebiopsyRequests(null)
                .withRecommendations(null)
                .withResponses(null)
                .withStudyInclusionRequests(null)
                .withSpecimens(null)
                .build()

            mtbFile.pseudonymizeWith(pseudonymizeService)
            mtbFile.anonymizeContentWith(pseudonymizeService)

            assertThat(mtbFile.episode.id).isNotNull()
        }
    }

    @Nested
    inner class UsingDnpmV2Datamodel {

        val FAKE_MTB_FILE_PATH = "mv64e-mtb-fake-patient.json"
        val CLEAN_PATIENT_ID = "aca5a971-28be-4089-8128-0036a4fe6f1a"

        private fun fakeMtbFile(): Mtb {
            val mtbFile = ClassPathResource(FAKE_MTB_FILE_PATH).inputStream
            return ObjectMapper().readValue(mtbFile, Mtb::class.java)
        }

        private fun Mtb.serialized(): String {
            return ObjectMapper().writeValueAsString(this)
        }

        @Test
        fun shouldNotContainCleanPatientId(@Mock pseudonymizeService: PseudonymizeService) {
            doAnswer {
                it.arguments[0]
                "PSEUDO-ID"
            }.whenever(pseudonymizeService).patientPseudonym(anyValueClass())

            val mtbFile = fakeMtbFile()

            mtbFile.pseudonymizeWith(pseudonymizeService)

            assertThat(mtbFile.patient.id).isEqualTo("PSEUDO-ID")
            assertThat(mtbFile.serialized()).doesNotContain(CLEAN_PATIENT_ID)
        }

        @Test
        fun shouldNotThrowExceptionOnNullValues(@Mock pseudonymizeService: PseudonymizeService) {
            doAnswer {
                it.arguments[0]
                "PSEUDO-ID"
            }.whenever(pseudonymizeService).patientPseudonym(anyValueClass())

            doAnswer {
                "TESTDOMAIN"
            }.whenever(pseudonymizeService).prefix()

            val mtbFile = Mtb().apply {
                this.patient = dev.pcvolkmer.mv64e.mtb.Patient().apply {
                    this.id = "PID"
                    this.birthDate = Date.from(Instant.now())
                    this.gender = GenderCoding().apply {
                        this.code = GenderCodingCode.MALE
                    }
                }
                this.episodesOfCare = listOf(
                    MtbEpisodeOfCare().apply {
                        this.id = "1"
                        this.patient = Reference().apply {
                            this.id = "PID"
                        }
                        this.period = PeriodDate().apply {
                            this.start = Date.from(Instant.now())
                        }
                    }
                )
            }

            mtbFile.pseudonymizeWith(pseudonymizeService)
            mtbFile.anonymizeContentWith(pseudonymizeService)

            assertThat(mtbFile.episodesOfCare).hasSize(1)
            assertThat(mtbFile.episodesOfCare.map { it.id }).isNotNull
        }

        @Test
        fun shouldNotContainAnyUuidAfterRehashingOfIds(@Mock pseudonymizeService: PseudonymizeService) {
            doAnswer {
                it.arguments[0]
                "PSEUDO-ID"
            }.whenever(pseudonymizeService).patientPseudonym(anyValueClass())

            doAnswer {
                "TESTDOMAIN"
            }.whenever(pseudonymizeService).prefix()

            val mtbFile = fakeMtbFile()

            /**
             * replace hex values with random long, so our test does not match false positives
              */
            mtbFile.ngsReports.forEach { report ->
                report.results.simpleVariants.forEach { simpleVariant ->
                    simpleVariant.externalIds.forEach { extIdValue ->
                        extIdValue.value =
                            Math.random().toLong().toString()
                    }
                }
            }
            mtbFile.ngsReports.forEach { report ->
                report.results.rnaFusions.forEach { simpleVariant ->
                    simpleVariant.externalIds.forEach { extIdValue ->
                        extIdValue.value =
                            Math.random().toLong().toString()
                    }
                }
            }

            mtbFile.pseudonymizeWith(pseudonymizeService)
            mtbFile.anonymizeContentWith(pseudonymizeService)

            val pattern =
                "\"[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\"".toRegex()
                    .toPattern()
            val matcher = pattern.matcher(mtbFile.serialized())

            assertThrows<IllegalStateException> {
                matcher.find()
                matcher.group()
            }.also {
                assertThat(it.message).isEqualTo("No match found")
            }
        }
    }
}
