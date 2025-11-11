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

@ExtendWith(MockitoExtension::class)
class ConsentProcessorTest {

  lateinit var consentProcessor: ConsentProcessor

  val objectMapper = ObjectMapper()
  val fhirContext = FhirContext.forR4()

  @BeforeEach
  fun setup(@Mock consentService: IConsentService) {
    val appConfigProperties = AppConfigProperties()
    val gIcsConfigProperties = GIcsConfigProperties("http://localhost")

    this.consentProcessor =
        ConsentProcessor(
            appConfigProperties,
            gIcsConfigProperties,
            objectMapper,
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
