package dev.dnpm.etl.processor.config

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import org.hl7.fhir.r4.model.Consent

class ConsentResourceSerializer : JsonSerializer<Consent>() {
    override fun serialize(
        value: Consent, gen: JsonGenerator, serializers: SerializerProvider
    ) {
        val json = JacksonConfig.fhirContext().newJsonParser().encodeResourceToString(value)
        gen.writeRawValue(json)
    }
}