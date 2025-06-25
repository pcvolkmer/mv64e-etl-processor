package dev.dnpm.etl.processor.config


import com.fasterxml.jackson.databind.module.SimpleModule
import org.hl7.fhir.instance.model.api.IBaseResource

class FhirResourceModule : SimpleModule() {
    init {
        addSerializer(IBaseResource::class.java, IBaseResourceSerializer())
        addDeserializer(IBaseResource::class.java, IBaseResourceDeserializer())
    }
}