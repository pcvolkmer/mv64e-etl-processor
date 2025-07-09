package dev.dnpm.etl.processor.config

import com.fasterxml.jackson.core.JsonParser

import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import org.hl7.fhir.r4.model.Consent

class ConsentResourceDeserializer : JsonDeserializer<Consent>() {
    override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): Consent {

        val jsonNode = p?.readValueAsTree<JsonNode>()
        val json = jsonNode?.toString()

        return JacksonConfig.fhirContext().newJsonParser().parseResource(json) as Consent
    }
}