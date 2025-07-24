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

package dev.dnpm.etl.processor.pseudonym

import de.ukw.ccc.bwhc.dto.*
import dev.dnpm.etl.processor.config.PseudonymizeConfigProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class PseudonymizeServiceTest {

    private val mtbFile = MtbFile.builder()
        .withPatient(
            Patient.builder()
                .withId("123")
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
                .withPatient("123")
                .withPeriod(PeriodStart("2023-08-08"))
                .build()
        )
        .build()

    @Test
    fun shouldNotUsePseudonymPrefixForGpas(@Mock generator: GpasPseudonymGenerator) {
        doAnswer {
            it.arguments[0]
        }.whenever(generator).generate(anyString())

        val pseudonymizeService = PseudonymizeService(generator, PseudonymizeConfigProperties())

        mtbFile.pseudonymizeWith(pseudonymizeService)

        assertThat(mtbFile.patient.id).isEqualTo("123")
    }

    @Test
    fun sanitizeFileName() {
        val result = GpasPseudonymGenerator.sanitizeValue("l://a\\bs;1*2?3>")

        assertThat(result).isEqualTo("l___a_bs_1_2_3_")
    }

    @Test
    fun shouldUsePseudonymPrefixForBuiltin(@Mock generator: AnonymizingGenerator) {
        doAnswer {
            it.arguments[0]
        }.whenever(generator).generate(anyString())

        val pseudonymizeService = PseudonymizeService(generator, PseudonymizeConfigProperties())

        mtbFile.pseudonymizeWith(pseudonymizeService)

        assertThat(mtbFile.patient.id).isEqualTo("UNKNOWN_123")
    }

    @Test
    fun shouldReturnDifferentValues() {
        val ag = AnonymizingGenerator()

        val tans = HashSet<String>()

        (1..1000).forEach { i ->
            val tan = ag.generateGenomDeTan("12345")
            assertThat(tan).hasSize(64)
            assertThat(tans.add(tan)).`as`("never the same result!").isTrue
        }
    }
}