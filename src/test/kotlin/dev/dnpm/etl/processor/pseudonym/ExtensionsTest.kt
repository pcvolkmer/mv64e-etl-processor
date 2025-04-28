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
import dev.pcvolkmer.mv64e.mtb.MTBEpisodeOfCare
import dev.pcvolkmer.mv64e.mtb.Mtb
import dev.pcvolkmer.mv64e.mtb.PeriodDate
import dev.pcvolkmer.mv64e.mtb.Reference
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

            val pattern = "\"[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\"".toRegex().toPattern()
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
        val CLEAN_PATIENT_ID = "63f8fd7b-8127-4f3c-8843-aa9199e21c29"

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

            val mtbFile = Mtb.builder()
                .withPatient(
                    dev.pcvolkmer.mv64e.mtb.Patient.builder()
                        .withId("1")
                        .withBirthDate("2000-08-08")
                        .withGender(null)
                        .build()
                )
                .withEpisodesOfCare(
                    listOf(
                        MTBEpisodeOfCare.builder()
                            .withId("1")
                            .withPatient(Reference("1"))
                            .withPeriod(PeriodDate.builder().withStart("2023-08-08").build())
                            .build()
                    )
                )
                .withClaims(null)
                .withDiagnoses(null)
                .withCarePlans(null)
                .withClaimResponses(null)
                .withHistologyReports(null)
                .withNgsReports(null)
                .withResponses(null)
                .withSpecimens(null)
                .build()

            mtbFile.pseudonymizeWith(pseudonymizeService)
            mtbFile.anonymizeContentWith(pseudonymizeService)

            assertThat(mtbFile.episodesOfCare).hasSize(1)
            assertThat(mtbFile.episodesOfCare.map { it.id }).isNotNull
        }
    }
}
