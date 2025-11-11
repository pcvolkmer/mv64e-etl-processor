package dev.dnpm.etl.processor.config

import com.fasterxml.jackson.databind.module.SimpleModule
import org.hl7.fhir.r4.model.Consent

class FhirResourceModule : SimpleModule() {
    init {
        addSerializer(Consent::class.java, ConsentResourceSerializer())
        addDeserializer(Consent::class.java, ConsentResourceDeserializer())
    }
}
