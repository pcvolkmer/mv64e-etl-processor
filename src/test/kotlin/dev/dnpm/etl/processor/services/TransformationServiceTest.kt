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
import dev.pcvolkmer.mv64e.mtb.ConsentProvision
import dev.pcvolkmer.mv64e.mtb.ModelProjectConsent
import dev.pcvolkmer.mv64e.mtb.ModelProjectConsentPurpose
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import dev.pcvolkmer.mv64e.mtb.Mtb
import dev.pcvolkmer.mv64e.mtb.MvhMetadata
import dev.pcvolkmer.mv64e.mtb.Provision
import org.hl7.fhir.instance.model.api.IBaseResource
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
        val mvhMetadata = MvhMetadata.builder().transferTan("transfertan12345").build()

        assertThat(mvhMetadata).isNotNull
        mvhMetadata.modelProjectConsent =
            ModelProjectConsent.builder().date(Date.from(Instant.parse("2025-06-23T00:00:00.00Z")))
                .version("1").provisions(
                    listOf(
                        Provision.builder().type(ConsentProvision.PERMIT)
                            .purpose(ModelProjectConsentPurpose.SEQUENCING)
                            .date(Date.from(Instant.parse("2025-06-23T00:00:00.00Z"))).build(),
                        Provision.builder().type(ConsentProvision.PERMIT)
                            .purpose(ModelProjectConsentPurpose.REIDENTIFICATION)
                            .date(Date.from(Instant.parse("2025-06-23T00:00:00.00Z"))).build(),
                        Provision.builder().type(ConsentProvision.DENY)
                            .purpose(ModelProjectConsentPurpose.CASE_IDENTIFICATION)
                            .date(Date.from(Instant.parse("2025-06-23T00:00:00.00Z"))).build()
                    )
                ).build()
        val consent = ConsentProcessorTest.getDummyGenomDeConsent()

        mvhMetadata.researchConsents = mutableListOf()
        mvhMetadata.researchConsents.add(mapOf(consent.id to consent as IBaseResource))

        val mtbFile = Mtb.builder().metadata(mvhMetadata).build()

        val transformed = service.transform(mtbFile)
        assertThat(transformed.metadata.modelProjectConsent.date).isNotNull

    }


}