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

import ca.uhn.fhir.context.FhirContext
import com.fasterxml.jackson.databind.ObjectMapper
import dev.dnpm.etl.processor.config.AppConfigProperties
import dev.dnpm.etl.processor.config.GIcsConfigProperties
import dev.dnpm.etl.processor.config.JacksonConfig
import dev.dnpm.etl.processor.consent.ConsentByMtbFile
import dev.dnpm.etl.processor.services.ConsentProcessor
import dev.dnpm.etl.processor.services.ConsentProcessorTest
import dev.pcvolkmer.mv64e.mtb.*
import org.assertj.core.api.Assertions.assertThat
import org.hl7.fhir.r4.model.Bundle
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
    fun getObjectMapper(): ObjectMapper {
        return JacksonConfig().objectMapper()
    }

    @Nested
    inner class UsingDnpmV2Datamodel {

        val FAKE_MTB_FILE_PATH = "mv64e-mtb-fake-patient.json"
        val CLEAN_PATIENT_ID = "644bae7a-56f6-4ee8-b02f-c532e65af5b1"

        private fun fakeMtbFile(): Mtb {
            val mtbFile = ClassPathResource(FAKE_MTB_FILE_PATH).inputStream
            return getObjectMapper().readValue(mtbFile, Mtb::class.java)
        }

        private fun Mtb.serialized(): String {
            return getObjectMapper().writeValueAsString(this)
        }

        @Test
        fun shouldNotContainCleanPatientId(@Mock pseudonymizeService: PseudonymizeService) {
            doAnswer {
                it.arguments[0]
                "PSEUDO-ID"
            }.whenever(pseudonymizeService).patientPseudonym(anyValueClass())

            val mtbFile = fakeMtbFile()
            mtbFile.ensureMetaDataIsInitialized()
            addConsentData(mtbFile)

            mtbFile.pseudonymizeWith(pseudonymizeService)

            assertThat(mtbFile.patient.id).isEqualTo("PSEUDO-ID")
            assertThat(mtbFile.serialized()).doesNotContain(CLEAN_PATIENT_ID)
        }

        private fun addConsentData(mtbFile: Mtb) {
            val gIcsConfigProperties = GIcsConfigProperties("", "", "")
            val appConfigProperties = AppConfigProperties(emptyList())

            val bundle = Bundle()
            val dummyConsent = ConsentProcessorTest.getDummyGenomDeConsent()
            dummyConsent.patient.reference = "Patient/$CLEAN_PATIENT_ID"
            bundle.addEntry().resource = dummyConsent

            ConsentProcessor(
                appConfigProperties,
                gIcsConfigProperties,
                JacksonConfig().objectMapper(),
                FhirContext.forR4(),
                ConsentByMtbFile()
            ).embedBroadConsentResources(mtbFile, bundle)

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
                    simpleVariant.fusionPartner3Prime?.transcriptId?.value =  Math.random().toLong().toString()
                    simpleVariant.fusionPartner5Prime?.transcriptId?.value =  Math.random().toLong().toString()
                    simpleVariant.externalIds?.forEach { it-> it?.value = Math.random().toLong().toString()  }
                }
            }

            mtbFile.pseudonymizeWith(pseudonymizeService)
            mtbFile.anonymizeContentWith(pseudonymizeService)

            val pattern =
                "\"[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\"".toRegex()
                    .toPattern()
            val input = mtbFile.serialized()
            val matcher = pattern.matcher(input)

            assertThrows<IllegalStateException> {
                matcher.find()
                val posSt= "found at pos: "+matcher.start().toString()  +", "+ matcher.end()
                System.out.println(posSt + " with "+     matcher.group())
            }.also {
                assertThat(it.message).isEqualTo("No match found")
            }
        }
    }
}
