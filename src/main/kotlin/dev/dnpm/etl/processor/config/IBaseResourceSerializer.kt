package dev.dnpm.etl.processor.config

import ca.uhn.fhir.context.FhirContext
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import org.hl7.fhir.instance.model.api.IBaseResource

class IBaseResourceSerializer : JsonSerializer<IBaseResource>() {
    override fun serialize(
        value: IBaseResource,
        gen: JsonGenerator,
        serializers: SerializerProvider
    ) {
        val fhirContext = FhirContext.forR4()
        val json = fhirContext.newJsonParser().encodeResourceToString(value)
        gen.writeRawValue(json)
    }
}