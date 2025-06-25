package dev.dnpm.etl.processor.config

import ca.uhn.fhir.context.FhirContext
import com.fasterxml.jackson.core.JsonParser

import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import org.hl7.fhir.instance.model.api.IBaseResource

class IBaseResourceDeserializer : JsonDeserializer<IBaseResource>() {
    override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): IBaseResource {
        val fhirContext = FhirContext.forR4()

        val jsonNode = p?.readValueAsTree<JsonNode>()
        val json = jsonNode?.toString()

        return fhirContext.newJsonParser().parseResource(json) as IBaseResource
    }
}