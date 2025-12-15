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

import ca.uhn.fhir.context.FhirContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import dev.dnpm.etl.processor.config.JacksonConfig
import dev.pcvolkmer.mv64e.mtb.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

class TransformationServiceTest {

  private lateinit var service: TransformationService

  @BeforeEach
  fun setup() {
    this.service =
        TransformationService(
            JacksonConfig().objectMapper(),
            listOf(
                Transformation.of("diagnoses[*].code.version") from "2013" to "2014",
            ),
        )
  }

  @Test
  fun shouldTransformMtbFile() {
    val mtbFile =
        Mtb.builder()
            .diagnoses(
                listOf(
                    MtbDiagnosis.builder()
                        .id("1234")
                        .code(Coding.builder().code("F79.9").version("2013").build())
                        .build()
                )
            )
            .build()

    val actual = this.service.transform(mtbFile)

    assertThat(actual).isNotNull
    assertThat(actual.diagnoses[0].code.version).isEqualTo("2014")
  }

  @Test
  fun shouldOnlyTransformGivenValues() {
    val mtbFile =
        Mtb.builder()
            .diagnoses(
                listOf(
                    MtbDiagnosis.builder()
                        .id("1234")
                        .code(Coding.builder().code("F79.9").version("2013").build())
                        .build(),
                    MtbDiagnosis.builder()
                        .id("1234")
                        .code(Coding.builder().code("F79.8").version("2019").build())
                        .build(),
                )
            )
            .build()

    val actual = this.service.transform(mtbFile)

    assertThat(actual).isNotNull
    assertThat(actual.diagnoses[0].code.code).isEqualTo("F79.9")
    assertThat(actual.diagnoses[0].code.version).isEqualTo("2014")
    assertThat(actual.diagnoses[1].code.code).isEqualTo("F79.8")
    assertThat(actual.diagnoses[1].code.version).isEqualTo("2019")
  }

  @Test
  fun shouldTransformConsentValues() {
    val mtbFile =
        Mtb.builder()
            .diagnoses(
                listOf(
                    MtbDiagnosis.builder()
                        .id("1234")
                        .code(Coding.builder().code("F79.9").version("2013").build())
                        .build(),
                    MtbDiagnosis.builder()
                        .id("1234")
                        .code(Coding.builder().code("F79.8").version("2019").build())
                        .build(),
                )
            )
            .build()

    val actual = this.service.transform(mtbFile)

    assertThat(actual).isNotNull
    assertThat(actual.diagnoses[0].code.code).isEqualTo("F79.9")
    assertThat(actual.diagnoses[0].code.version).isEqualTo("2014")
    assertThat(actual.diagnoses[1].code.code).isEqualTo("F79.8")
    assertThat(actual.diagnoses[1].code.version).isEqualTo("2019")
  }

  @Test
  fun shouldTransformConsent() {
    val mvhMetadata = MvhMetadata.builder().transferTan("transfertan12345").build()

    assertThat(mvhMetadata).isNotNull
    mvhMetadata.modelProjectConsent =
        ModelProjectConsent.builder()
            .date(Date.from(Instant.parse("2025-08-15T00:00:00.00Z")))
            .version("1")
            .provisions(
                listOf(
                    Provision.builder()
                        .type(ConsentProvision.PERMIT)
                        .purpose(ModelProjectConsentPurpose.SEQUENCING)
                        .date(Date.from(Instant.parse("2025-08-15T00:00:00.00Z")))
                        .build(),
                    Provision.builder()
                        .type(ConsentProvision.PERMIT)
                        .purpose(ModelProjectConsentPurpose.REIDENTIFICATION)
                        .date(Date.from(Instant.parse("2025-08-15T00:00:00.00Z")))
                        .build(),
                    Provision.builder()
                        .type(ConsentProvision.DENY)
                        .purpose(ModelProjectConsentPurpose.CASE_IDENTIFICATION)
                        .date(Date.from(Instant.parse("2025-08-15T00:00:00.00Z")))
                        .build(),
                )
            )
            .build()
    val consent = ConsentProcessorTest.getDummyGenomDeConsent()
    val jsonNode = ObjectMapper().readValue(FhirContext.forR4().newJsonParser().encodeToString(consent), ObjectNode::class.java)

    mvhMetadata.researchConsents = mutableListOf()
    mvhMetadata.researchConsents.add(MvhMetadata.ResearchConsent.from(jsonNode))

    val mtbFile = Mtb.builder().metadata(mvhMetadata).build()

    val transformed = service.transform(mtbFile)
    assertThat(transformed.metadata.modelProjectConsent.date).isNotNull
  }
}
