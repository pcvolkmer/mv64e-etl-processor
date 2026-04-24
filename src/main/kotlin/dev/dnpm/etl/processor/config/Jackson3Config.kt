package dev.dnpm.etl.processor.config

import ca.uhn.fhir.context.FhirContext
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.hl7.fhir.r4.model.Consent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.*
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule

@Configuration
class Jackson3Config {

    @Bean
    fun jsonMapper(): JsonMapper =
        JsonMapper
            .builder()
            .addModule(JacksonFhirResourceModule())
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
        val json = JacksonConfig.fhirContext().newJsonParser().encodeResourceToString(value)
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

        return JacksonConfig.fhirContext().newJsonParser().parseResource(json) as Consent
    }
}
