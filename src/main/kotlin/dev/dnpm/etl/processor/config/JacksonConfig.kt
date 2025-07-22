package dev.dnpm.etl.processor.config

import ca.uhn.fhir.context.FhirContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule

@Configuration
class JacksonConfig {

    companion object {
        var fhirContext: FhirContext = FhirContext.forR4()

        @JvmStatic
        fun fhirContext(): FhirContext {
            return fhirContext
        }
    }

    @Bean
    fun objectMapper(): ObjectMapper = ObjectMapper().registerModule(FhirResourceModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).registerModule(
            JavaTimeModule()
        )
}
