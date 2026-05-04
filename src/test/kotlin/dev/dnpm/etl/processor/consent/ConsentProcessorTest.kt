/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2023       Comprehensive Cancer Center Mainfranken
 * Copyright (c) 2026  Paul-Christian Volkmer, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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

import ca.uhn.fhir.context.FhirContext
import com.fasterxml.jackson.databind.ObjectMapper
import dev.dnpm.etl.processor.config.AppConfigProperties
import dev.dnpm.etl.processor.config.GIcsConfigProperties
import dev.dnpm.etl.processor.services.ConsentProcessor
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Consent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import tools.jackson.databind.json.JsonMapper

@ExtendWith(MockitoExtension::class)
class ConsentProcessorTest {

  lateinit var consentProcessor: ConsentProcessor

  val jsonMapper = JsonMapper()
  val fhirContext = FhirContext.forR4()

  @BeforeEach
  fun setup(@Mock consentService: IConsentService) {
    val appConfigProperties = AppConfigProperties()
    val gIcsConfigProperties = GIcsConfigProperties("http://localhost")

    this.consentProcessor =
        ConsentProcessor(
            appConfigProperties,
            gIcsConfigProperties,
            jsonMapper,
            fhirContext,
            consentService,
        )
  }

  @ParameterizedTest
  @CsvSource(value = ["permittedConsentBundle.json,permit", "deniedConsentBundle.json,deny"])
  fun checkGetProvisionTypeByPolicyCode(filename: String, expected: String) {
    val bundle =
        fhirContext
            .newJsonParser()
            .parseResource(this.javaClass.classLoader.getResourceAsStream(filename))
    assertThat(bundle).isInstanceOf(Bundle::class.java)

    val actual =
        consentProcessor.getProvisionTypeByPolicyCode(
            bundle as Bundle,
            Date(),
            ConsentDomain.BROAD_CONSENT,
        )

    assertThat(actual).isEqualTo(Consent.ConsentProvisionType.valueOf(expected.uppercase()))
  }
}
