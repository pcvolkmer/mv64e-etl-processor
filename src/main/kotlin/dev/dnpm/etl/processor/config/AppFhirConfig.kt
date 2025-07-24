package dev.dnpm.etl.processor.config

import ca.uhn.fhir.context.FhirContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class AppFhirConfig {
    private val fhirCtx: FhirContext = FhirContext.forR4()

    @Bean
    fun fhirContext(): FhirContext {
        return fhirCtx
    }
}