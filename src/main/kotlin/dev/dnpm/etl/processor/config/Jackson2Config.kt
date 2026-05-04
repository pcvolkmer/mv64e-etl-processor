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
class JacksonConfig {
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
        val json = JacksonConfig.fhirContext().newJsonParser().encodeResourceToString(value)
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

        return JacksonConfig.fhirContext().newJsonParser().parseResource(json) as Consent
    }
}
