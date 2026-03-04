package dev.dnpm.etl.processor.config

import ca.uhn.fhir.context.FhirContext
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JacksonConfig {
    companion object {
        var fhirContext: FhirContext = FhirContext.forR4()

        @JvmStatic fun fhirContext(): FhirContext = fhirContext
    }

    @Bean
    fun objectMapper(): ObjectMapper =
        ObjectMapper()
            .registerModule(FhirResourceModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .registerModule(JavaTimeModule())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
}
