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

import de.ukw.ccc.bwhc.dto.Consent
import de.ukw.ccc.bwhc.dto.Diagnosis
import de.ukw.ccc.bwhc.dto.Icd10
import de.ukw.ccc.bwhc.dto.MtbFile
import dev.dnpm.etl.processor.config.JacksonConfig
import dev.pcvolkmer.mv64e.mtb.ModelProjectConsent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import dev.pcvolkmer.mv64e.mtb.Mtb
import dev.pcvolkmer.mv64e.mtb.MvhMetadata
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import java.time.Instant
import java.util.Date

class TransformationServiceTest {

    private lateinit var service: TransformationService

    @BeforeEach
    fun setup() {
        this.service = TransformationService(
            JacksonConfig().objectMapper(), listOf(
                Transformation.of("consent.status") from Consent.Status.ACTIVE to Consent.Status.REJECTED,
                Transformation.of("diagnoses[*].icd10.version") from "2013" to "2014",
            )
        )
    }

    @Test
    fun shouldTransformMtbFile() {
        val mtbFile = MtbFile.builder().withDiagnoses(
            listOf(
                Diagnosis.builder().withId("1234").withIcd10(Icd10("F79.9").also {
                    it.version = "2013"
                }).build()
            )
        ).build()

        val actual = this.service.transform(mtbFile)

        assertThat(actual).isNotNull
        assertThat(actual.diagnoses[0].icd10.version).isEqualTo("2014")
    }

    @Test
    fun shouldOnlyTransformGivenValues() {
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

        val actual = this.service.transform(mtbFile)

        assertThat(actual).isNotNull
        assertThat(actual.diagnoses[0].icd10.code).isEqualTo("F79.9")
        assertThat(actual.diagnoses[0].icd10.version).isEqualTo("2014")
        assertThat(actual.diagnoses[1].icd10.code).isEqualTo("F79.8")
        assertThat(actual.diagnoses[1].icd10.version).isEqualTo("2019")
    }

    @Test
    fun shouldTransformMtbFileWithConsentEnum() {
        val mtbFile = MtbFile.builder().withConsent(
            Consent("123", "456", Consent.Status.ACTIVE)
        ).build()

        val actual = this.service.transform(mtbFile)

        assertThat(actual.consent).isNotNull
        assertThat(actual.consent.status).isEqualTo(Consent.Status.REJECTED)
    }

    @Test
    fun shouldTransformConsentValues() {
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

        val actual = this.service.transform(mtbFile)

        assertThat(actual).isNotNull
        assertThat(actual.diagnoses[0].icd10.code).isEqualTo("F79.9")
        assertThat(actual.diagnoses[0].icd10.version).isEqualTo("2014")
        assertThat(actual.diagnoses[1].icd10.code).isEqualTo("F79.8")
        assertThat(actual.diagnoses[1].icd10.version).isEqualTo("2019")
    }

    @Test
    fun shouldTransformConsent() {
        val mvhMetadata = MvhMetadata.builder().transferTan("transfertan12345").build();

        assertThat(mvhMetadata).isNotNull
        mvhMetadata.modelProjectConsent =
            ModelProjectConsent.builder().date(Date.from(Instant.now())).version("1").build()
        val consent1 = org.hl7.fhir.r4.model.Consent()
        consent1.id = "consent 1 id"
        consent1.patient.reference = "Patient/1234-pat1"

        consent1.provision.setType(org.hl7.fhir.r4.model.Consent.ConsentProvisionType.fromCode("deny"))
        consent1.provision.period.start = Date.from(Instant.parse("2025-06-23T00:00:00.00Z"))
        consent1.provision.period.end = Date.from(Instant.parse("3000-01-01T00:00:00.00Z"))


        val addProvision1 = consent1.provision.addProvision()
        addProvision1.setType(org.hl7.fhir.r4.model.Consent.ConsentProvisionType.fromCode("permit"))
        addProvision1.period.start = Date.from(Instant.parse("2025-06-23T00:00:00.00Z"))
        addProvision1.period.end = Date.from(Instant.parse("3000-01-01T00:00:00.00Z"))
        addProvision1.code.addLast(
            CodeableConcept(
                Coding(
                    "https://ths-greifswald.de/fhir/CodeSystem/gics/Policy/GenomDE_MV",
                    "Teilnahme",
                    "Teilnahme am Modellvorhaben und Einwilligung zur Genomsequenzierung"
                )
            )
        )

        val addProvision2 = consent1.provision.addProvision()
        addProvision2.setType(org.hl7.fhir.r4.model.Consent.ConsentProvisionType.fromCode("deny"))
        addProvision2.period.start = Date.from(Instant.parse("2025-06-23T00:00:00.00Z"))
        addProvision2.period.end = Date.from(Instant.parse("3000-01-01T00:00:00.00Z"))
        addProvision2.code.addLast(
            CodeableConcept(
                Coding(
                    "https://ths-greifswald.de/fhir/CodeSystem/gics/Policy/GenomDE_MV",
                    "Rekontaktierung",
                    "Re-Identifizierung meiner Daten über die Vertrauensstelle beim Robert Koch-Institut und in die erneute Kontaktaufnahme durch meine behandelnde Ärztin oder meinen behandelnden Arzt"
                )
            )
        )

        mvhMetadata.researchConsents =  mutableListOf()
        mvhMetadata.researchConsents.add(mapOf(consent1.id to consent1 as IBaseResource))

        val mtbFile = Mtb.builder().metadata(mvhMetadata).build()

        val transformed = service.transform(mtbFile)
        assertThat(transformed.metadata.modelProjectConsent.date).isNotNull

    }
}