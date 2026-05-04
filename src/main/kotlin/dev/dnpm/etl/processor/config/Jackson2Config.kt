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

package dev.dnpm.etl.processor.config

import ca.uhn.fhir.context.FhirContext
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.hl7.fhir.r4.model.Consent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class Jackson2Config {
    companion object {
        var fhirContext: FhirContext = FhirContext.forR4()

        @JvmStatic fun fhirContext(): FhirContext = fhirContext
    }

    @Bean
    fun objectMapper(): com.fasterxml.jackson.databind.ObjectMapper =
        com.fasterxml.jackson.databind
            .ObjectMapper()
            .registerModule(Jackson2FhirResourceModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .registerModule(JavaTimeModule())
            .registerModule(Jdk8Module())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
}

class Jackson2FhirResourceModule : com.fasterxml.jackson.databind.module.SimpleModule() {
    init {
        addSerializer(Consent::class.java, Jackson2ConsentResourceSerializer())
        addDeserializer(Consent::class.java, Jackson2ConsentResourceDeserializer())
    }
}

class Jackson2ConsentResourceSerializer : com.fasterxml.jackson.databind.JsonSerializer<Consent>() {
    override fun serialize(
        value: Consent,
        gen: JsonGenerator,
        serializers: com.fasterxml.jackson.databind.SerializerProvider,
    ) {
        val json = Jackson2Config.fhirContext().newJsonParser().encodeResourceToString(value)
        gen.writeRawValue(json)
    }
}

class Jackson2ConsentResourceDeserializer : com.fasterxml.jackson.databind.JsonDeserializer<Consent>() {
    override fun deserialize(
        p: JsonParser?,
        ctxt: com.fasterxml.jackson.databind.DeserializationContext?,
    ): Consent {
        val jsonNode = p?.readValueAsTree<com.fasterxml.jackson.databind.JsonNode>()
        val json = jsonNode?.toString()

        return Jackson2Config.fhirContext().newJsonParser().parseResource(json) as Consent
    }
}
