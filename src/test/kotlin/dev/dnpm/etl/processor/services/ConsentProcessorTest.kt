package dev.dnpm.etl.processor.services

import ca.uhn.fhir.context.FhirContext
import com.fasterxml.jackson.databind.ObjectMapper
import dev.dnpm.etl.processor.config.AppConfigProperties
import dev.dnpm.etl.processor.config.GIcsConfigProperties
import dev.dnpm.etl.processor.config.JacksonConfig
import dev.dnpm.etl.processor.consent.ConsentDomain
import dev.dnpm.etl.processor.consent.GicsConsentService
import dev.pcvolkmer.mv64e.mtb.*
import org.assertj.core.api.Assertions.assertThat
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Consent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.core.io.ClassPathResource
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*

@ExtendWith(MockitoExtension::class)
class ConsentProcessorTest {

    private lateinit var appConfigProperties: AppConfigProperties
    private lateinit var gicsConsentService: GicsConsentService
    private lateinit var objectMapper: ObjectMapper
    private lateinit var gIcsConfigProperties: GIcsConfigProperties
    private lateinit var fhirContext: FhirContext
    private lateinit var consentProcessor: ConsentProcessor

    @BeforeEach
    fun setups(
        @Mock gicsConsentService: GicsConsentService,
    ) {

        this.gIcsConfigProperties = GIcsConfigProperties(null, null, null, true)
        val jacksonConfig = JacksonConfig()
        this.objectMapper = jacksonConfig.objectMapper()
        this.fhirContext = JacksonConfig.fhirContext()
        this.gicsConsentService = gicsConsentService
        this.appConfigProperties = AppConfigProperties(null, emptyList())
        this.consentProcessor =
            ConsentProcessor(
                appConfigProperties,
                gIcsConfigProperties,
                objectMapper,
                fhirContext,
                gicsConsentService
            )
    }

    @Test
    fun consentOk() {
        assertThat(consentProcessor.toString()).isNotNull
        // prep gICS response
        doAnswer { getDummyBroadConsentBundle() }.whenever(gicsConsentService)
            .getConsent(any(), any(), eq(ConsentDomain.BroadConsent))

        doAnswer { Bundle() }.whenever(gicsConsentService)
            .getConsent(any(), any(), eq(ConsentDomain.Modelvorhaben64e))

        val inputMtb = Mtb.builder()
            .patient(Patient.builder().id("d611d429-5003-11f0-a144-661e92ac9503").build()).build()
        val checkResult = consentProcessor.consentGatedCheckAndTryEmbedding(inputMtb)

        assertThat(checkResult).isTrue
        assertThat(inputMtb.metadata.researchConsents).hasSize(13)
    }

    companion object {
        fun getDummyGenomDeConsent(): Consent {
            val consent = Consent()
            consent.id = "consent 1 id"
            consent.patient.reference = "Patient/1234-pat1"

            consent.provision.setType(
                Consent.ConsentProvisionType.fromCode(
                    "deny"
                )
            )
            consent.provision.period.start =
                Date.from(Instant.parse("2025-06-23T00:00:00.00Z"))
            consent.provision.period.end =
                Date.from(Instant.parse("3000-01-01T00:00:00.00Z"))

            val addProvision1 = consent.provision.addProvision()
            addProvision1.setType(Consent.ConsentProvisionType.fromCode("permit"))
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

            val addProvision2 = consent.provision.addProvision()
            addProvision2.setType(Consent.ConsentProvisionType.fromCode("deny"))
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
            return consent
        }
    }

    @ParameterizedTest
    @CsvSource(
        "2.16.840.1.113883.3.1937.777.24.5.3.8,urn:oid:2.16.840.1.113883.3.1937.777.24.5.3,2025-07-23T00:00:00+02:00,PERMIT,expect permit",
        "2.16.840.1.113883.3.1937.777.24.5.3.8,urn:oid:2.16.840.1.113883.3.1937.777.24.5.3,2025-06-23T00:00:00+02:00,PERMIT,expect permit date is exactly on start",
        "2.16.840.1.113883.3.1937.777.24.5.3.8,urn:oid:2.16.840.1.113883.3.1937.777.24.5.3,2055-06-23T00:00:00+02:00,PERMIT,expect permit date is exactly on end",
        "2.16.840.1.113883.3.1937.777.24.5.3.8,urn:oid:2.16.840.1.113883.3.1937.777.24.5.3,2021-06-23T00:00:00+02:00,NULL,date is before start",
        "2.16.840.1.113883.3.1937.777.24.5.3.8,urn:oid:2.16.840.1.113883.3.1937.777.24.5.3,2060-06-23T00:00:00+02:00,NULL,date is after end",
        "2.16.840.1.113883.3.1937.777.24.5.3.8,XXXX,2025-07-23T00:00:00+02:00,NULL,system not found - therefore expect NULL",
        "2.16.840.1.113883.3.1937.777.24.5.3.27,urn:oid:2.16.840.1.113883.3.1937.777.24.5.3,2025-07-23T00:00:00+02:00,DENY,provision is denied"
    )
    fun getProvisionTypeByPolicyCode(
        code: String?, system: String?, timeStamp: String, expected: String?,
        desc: String?
    ) {
        val testData = getDummyBroadConsentBundle()

        val requestDate = Date.from(OffsetDateTime.parse(timeStamp).toInstant())

        val result: Consent.ConsentProvisionType =
            consentProcessor.getProvisionTypeByPolicyCode(testData, code, system, requestDate)
        assertThat(result).isNotNull()


        assertThat(result).`as`(desc)
            .isEqualTo(Consent.ConsentProvisionType.valueOf(expected!!))
    }

    fun getDummyBroadConsentBundle(): Bundle {
        val bundle: InputStream?
        try {
            bundle = ClassPathResource(
                "fake_broadConsent_gics_response_permit.json"
            ).getInputStream()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        return FhirContext.forR4().newJsonParser()
            .parseResource<Bundle>(Bundle::class.java, bundle)
    }

}