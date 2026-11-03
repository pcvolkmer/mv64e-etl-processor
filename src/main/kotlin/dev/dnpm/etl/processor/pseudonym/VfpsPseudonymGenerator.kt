package dev.dnpm.etl.processor.pseudonym

import dev.dnpm.etl.processor.config.VfpsConfigProperties
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.retry.support.RetryTemplate
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.postForObject
import org.springframework.web.util.UriComponentsBuilder

class VfpsPseudonymGenerator(
    private val configProperties: VfpsConfigProperties,
    private val retryTemplate: RetryTemplate,
    private val restTemplate: RestTemplate,
) : Generator {
    private val uri =
        configProperties.uri?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("vFPS URI must be configured")

    private val headers =
        HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            configProperties.token
                ?.takeIf { it.isNotBlank() }
                ?.let { setBearerAuth(it) }
        }

    override fun generate(id: String): String =
        execute {
            retryTemplate.execute<String, Exception> {
                request(configProperties.patientNamespace, "pseudonyms", id)
            }
        }

    override fun generateGenomDeTan(id: String): String =
        execute {
            request(configProperties.genomDeTanNamespace, "pseudonyms:append", id)
        }

    private fun request(
        namespace: String,
        operation: String,
        id: String,
    ): String {
        val url =
            UriComponentsBuilder
                .fromUriString(uri)
                .pathSegment("v1", "namespaces", namespace, operation)
                .build()
                .encode()
                .toUri()

        val response =
            restTemplate.postForObject<VfpsResponse>(
                url,
                HttpEntity(mapOf("originalValue" to id), headers),
            )

        return response
            ?.pseudonym
            ?.pseudonymValue
            ?.takeIf { it.isNotBlank() }
            ?: throw PseudonymRequestFailed("No pseudonym returned from vFPS")
    }

    private fun execute(action: () -> String): String =
        try {
            action()
        } catch (ex: RestClientException) {
            throw PseudonymRequestFailed("vFPS request failed", ex)
        }
}

private data class VfpsResponse(
    val pseudonym: VfpsPseudonym? = null,
)

private data class VfpsPseudonym(
    val pseudonymValue: String? = null,
)
