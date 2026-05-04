/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2023       Comprehensive Cancer Center Mainfranken
 * Copyright (c) 2023-2026  Paul-Christian Volkmer, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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
import org.hl7.fhir.r4.model.Consent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.*
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.module.kotlin.KotlinModule

@Configuration
class Jackson3Config {

    companion object {
        var fhirContext: FhirContext = FhirContext.forR4()

        @JvmStatic fun fhirContext(): FhirContext = fhirContext
    }

    @Bean
    fun jsonMapper(): JsonMapper =
        JsonMapper
            .builder()
            .addModule(JacksonFhirResourceModule())
            .addModule(KotlinModule.Builder().build())
            .changeDefaultPropertyInclusion {
                it.withContentInclusion(JsonInclude.Include.NON_NULL)
                it.withValueInclusion(JsonInclude.Include.NON_NULL)
            }.build()
}

class JacksonFhirResourceModule : SimpleModule() {
    init {
        addSerializer(Consent::class.java, JacksonConsentResourceSerializer())
        addDeserializer(Consent::class.java, JacksonConsentResourceDeserializer())
    }
}

class JacksonConsentResourceSerializer : ValueSerializer<Consent>() {
    override fun serialize(
        value: Consent?,
        gen: tools.jackson.core.JsonGenerator?,
        ctxt: SerializationContext?,
    ) {
        val json = Jackson3Config.fhirContext().newJsonParser().encodeResourceToString(value)
        gen?.writeRawValue(json)
    }
}

class JacksonConsentResourceDeserializer : ValueDeserializer<Consent>() {
    override fun deserialize(
        p: tools.jackson.core.JsonParser?,
        ctxt: DeserializationContext?,
    ): Consent {
        val jsonNode = p?.readValueAsTree<JsonNode>()
        val json = jsonNode?.toString()

        return Jackson3Config.fhirContext().newJsonParser().parseResource(json) as Consent
    }
}
