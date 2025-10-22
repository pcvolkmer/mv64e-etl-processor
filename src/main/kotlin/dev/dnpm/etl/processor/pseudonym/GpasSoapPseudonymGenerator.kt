package dev.dnpm.etl.processor.pseudonym

import dev.dnpm.etl.processor.config.AppFhirConfig
import dev.dnpm.etl.processor.config.GPasConfigProperties
import org.springframework.retry.support.RetryTemplate

class GpasSoapPseudonymGenerator(
    private val gpasCfg: GPasConfigProperties,
    private val retryTemplate: RetryTemplate,
    private val gpasSoapService: GpasSoapService,
    private val appFhirConfig: AppFhirConfig
) : Generator {

    override fun generate(id: String): String {
        return retryTemplate.execute<String, Exception> {
            gpasSoapService.getOrCreatePseudonymFor(id, gpasCfg.patientDomain)
        }
    }

    override fun generateGenomDeTan(id: String): String {
        throw NotImplementedError()
    }
}

