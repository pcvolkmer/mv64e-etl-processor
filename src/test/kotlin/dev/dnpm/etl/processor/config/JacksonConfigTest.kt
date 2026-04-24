package dev.dnpm.etl.processor.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.*

class JacksonConfigTest {

    lateinit var jacksonConfig: JacksonConfig

    @BeforeEach
    fun setup() {
        this.jacksonConfig = JacksonConfig()
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

        val json = this.jacksonConfig.objectMapper().readTree(inputJson)
        val actual = this.jacksonConfig.objectMapper().writeValueAsString(json)

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

        val json = this.jacksonConfig.objectMapper().readTree(inputJson)
        val actual = this.jacksonConfig.objectMapper().writeValueAsString(json)

        assertThat(actual).contains(""""lastUpdated":"2025-08-15T11:13:59.143+02:00"""")
    }

}
