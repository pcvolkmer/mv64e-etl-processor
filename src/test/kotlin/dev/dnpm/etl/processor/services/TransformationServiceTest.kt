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

package dev.dnpm.etl.processor.services

import com.fasterxml.jackson.databind.ObjectMapper
import de.ukw.ccc.bwhc.dto.Consent
import de.ukw.ccc.bwhc.dto.Diagnosis
import de.ukw.ccc.bwhc.dto.Icd10
import de.ukw.ccc.bwhc.dto.MtbFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TransformationServiceTest {

    private lateinit var service: TransformationService

    @BeforeEach
    fun setup() {
        this.service = TransformationService(ObjectMapper())
    }

    @Test
    fun shouldTransformMtbFile() {
        val transformations = arrayOf(
            Transformation.of("diagnoses[*].icd10.version") from "2013" to "2014",
        )

        val mtbFile = MtbFile.builder().withDiagnoses(
            listOf(
                Diagnosis.builder().withId("1234").withIcd10(Icd10("F79.9").also {
                    it.version = "2013"
                }).build()
            )
        ).build()

        val actual = this.service.transform(mtbFile, *transformations)

        assertThat(actual).isNotNull
        assertThat(actual.diagnoses[0].icd10.version).isEqualTo("2014")
    }

    @Test
    fun shouldOnlyTransformGivenValues() {
        val transformations = arrayOf(
            Transformation.of("diagnoses[*].icd10.version") from "2013" to "2014",
        )

        val mtbFile = MtbFile.builder().withDiagnoses(
            listOf(
                Diagnosis.builder().withId("1234").withIcd10(Icd10("F79.9").also {
                    it.version = "2013"
                }).build(),
                Diagnosis.builder().withId("5678").withIcd10(Icd10("F79.8").also {
                    it.version = "2019"
                }).build()
            )
        ).build()

        val actual = this.service.transform(mtbFile, *transformations)

        assertThat(actual).isNotNull
        assertThat(actual.diagnoses[0].icd10.code).isEqualTo("F79.9")
        assertThat(actual.diagnoses[0].icd10.version).isEqualTo("2014")
        assertThat(actual.diagnoses[1].icd10.code).isEqualTo("F79.8")
        assertThat(actual.diagnoses[1].icd10.version).isEqualTo("2019")
    }

    @Test
    fun shouldTransformMtbFileWithConsentEnum() {
        val transformations = arrayOf(
            Transformation.of("consent.status") from Consent.Status.ACTIVE to Consent.Status.REJECTED,
        )

        val mtbFile = MtbFile.builder().withConsent(
            Consent("123", "456", Consent.Status.ACTIVE)
        ).build()

        val actual = this.service.transform(mtbFile, *transformations)

        assertThat(actual.consent).isNotNull
        assertThat(actual.consent.status).isEqualTo(Consent.Status.REJECTED)
    }

}