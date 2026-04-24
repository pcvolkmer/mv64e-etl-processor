package dev.dnpm.etl.processor.config

import com.fasterxml.jackson.databind.node.ObjectNode
import dev.pcvolkmer.mv64e.mtb.Mtb
import dev.pcvolkmer.mv64e.mtb.MvhMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.*

class Jackson3ConfigTest {

    lateinit var jacksonConfig: Jackson3Config

    @BeforeEach
    fun setup() {
        this.jacksonConfig = Jackson3Config()
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "mv64e-mtb-fake-patient.json",
        "fake_broadConsent_mii_response_deny.json",
        "fake_broadConsent_mii_response_permit.json",
    ])
    fun shouldSerializeJsonWithoutNulledOutFields(filename: String) {
        val inputJson =
            Objects.requireNonNull(this.javaClass.classLoader.getResourceAsStream(filename))?.readAllBytes()?.decodeToString()

        val jsonNode = this.jacksonConfig.jsonMapper().readTree(inputJson)
        val actual = this.jacksonConfig.jsonMapper().writeValueAsString(jsonNode)

        assertThat(actual).doesNotContain("null")
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "fake_broadConsent_mii_response_deny.json",
        "fake_broadConsent_mii_response_permit.json",
    ])
    fun shouldSerializeConsentWithoutWithoutDatesAsTimestamps(filename: String) {
        val inputJson =
            Objects.requireNonNull(this.javaClass.classLoader.getResourceAsStream(filename))?.readAllBytes()?.decodeToString()

        val json = this.jacksonConfig.jsonMapper().readTree(inputJson)
        val actual = this.jacksonConfig.jsonMapper().writeValueAsString(json)

        assertThat(actual).contains(""""lastUpdated":"2025-08-15T11:13:59.143+02:00"""")
    }

    @Test
    fun shouldSerializeJsonWithBroadConsent() {
        val inputMtbFileJson =
            Objects.requireNonNull(this.javaClass.classLoader.getResourceAsStream("mv64e-mtb-fake-patient.json"))?.readAllBytes()?.decodeToString()

        val inputConsentJson =
            Objects.requireNonNull(this.javaClass.classLoader.getResourceAsStream("fake_broadConsent_mii_response_permit.json"))?.readAllBytes()?.decodeToString()

        val mtb = this.jacksonConfig.jsonMapper().readValue<Mtb>(inputMtbFileJson, Mtb::class.java)
        // Still use Jackson2 ObjectMapper since MTB DTO requires Jackson2 ObjectNode
        val consentJsonNode = JacksonConfig().objectMapper().readTree(inputConsentJson)
        mtb.metadata = MvhMetadata.builder().researchConsents(listOf(MvhMetadata.ResearchConsent.from(consentJsonNode as ObjectNode))).build()

        val actual = this.jacksonConfig.jsonMapper().writeValueAsString(mtb)

        assertThat(actual).doesNotContain("null")
        assertThat(actual).contains(""""lastUpdated":"2025-08-15T11:13:59.143+02:00"""")
        assertThat(actual).contains("""{"entry":[{"fullUrl":"http://localhost:8080/ttp-fhir/fhir/gics/Consent/7d3456c2-79b1-11f0-ab27-6ed0ed82d0fd"""")
    }
}
