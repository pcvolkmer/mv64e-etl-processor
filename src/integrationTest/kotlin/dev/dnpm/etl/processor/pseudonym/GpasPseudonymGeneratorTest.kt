/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2025  Comprehensive Cancer Center Mainfranken, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.dnpm.etl.processor.pseudonym

import dev.dnpm.etl.processor.config.GPasConfigProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.retry.policy.SimpleRetryPolicy
import org.springframework.retry.support.RetryTemplateBuilder
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestTemplate
import java.io.IOException

class GpasPseudonymGeneratorTest {

    private lateinit var mockRestServiceServer: MockRestServiceServer
    private lateinit var generator: GpasPseudonymGenerator
    private lateinit var restTemplate: RestTemplate

    @BeforeEach
    fun setup() {
        val retryTemplate = RetryTemplateBuilder().customPolicy(SimpleRetryPolicy(1)).build()
        val gPasConfigProperties = GPasConfigProperties(
            "https://localhost/ttp-fhir/fhir/gpas/\$pseudonymizeAllowCreate",
            "test",
            null,
            null
        )

        this.restTemplate = RestTemplate()
        this.mockRestServiceServer = MockRestServiceServer.createServer(restTemplate)
        this.generator = GpasPseudonymGenerator(gPasConfigProperties, retryTemplate, restTemplate)
    }

    @Test
    fun shouldReturnExpectedPseudonym() {
        this.mockRestServiceServer.expect {
            method(HttpMethod.POST)
            requestTo("https://localhost/ttp-fhir/fhir/gpas/\$pseudonymizeAllowCreate")
        }.andRespond {
            withStatus(HttpStatus.OK).body(getDummyResponseBody("1234", "test", "test1234ABCDEF567890"))
                .createResponse(it)
        }

        assertThat(this.generator.generate("ID1234")).isEqualTo("test1234ABCDEF567890")
    }

    @Test
    fun shouldThrowExceptionIfGpasNotAvailable() {
        this.mockRestServiceServer.expect {
            method(HttpMethod.POST)
            requestTo("https://localhost/ttp-fhir/fhir/gpas/\$pseudonymizeAllowCreate")
        }.andRespond {
            withException(IOException("Simulated IO error")).createResponse(it)
        }

        assertThrows<PseudonymRequestFailed> { this.generator.generate("ID1234") }
    }

    @Test
    fun shouldThrowExceptionIfGpasDoesNotReturn2xxResponse() {
        this.mockRestServiceServer.expect {
            method(HttpMethod.POST)
            requestTo("https://localhost/ttp-fhir/fhir/gpas/\$pseudonymizeAllowCreate")
        }.andRespond {
            withStatus(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "https://localhost/ttp-fhir/fhir/gpas/\$pseudonymizeAllowCreate")
                .createResponse(it)
        }

        assertThrows<PseudonymRequestFailed> { this.generator.generate("ID1234") }
    }

    companion object {

        fun getDummyResponseBody(original: String, target: String, pseudonym: String) = """{
          "resourceType": "Parameters",
          "parameter": [
            {
              "name": "pseudonym",
              "part": [
                {
                  "name": "original",
                  "valueIdentifier": {
                    "system": "https://ths-greifswald.de/gpas",
                    "value": "$original"
                  }
                },
                {
                  "name": "target",
                  "valueIdentifier": {
                    "system": "https://ths-greifswald.de/gpas",
                    "value": "$target"
                  }
                },
                {
                  "name": "pseudonym",
                  "valueIdentifier": {
                    "system": "https://ths-greifswald.de/gpas",
                    "value": "$pseudonym"
                  }
                }
              ]
            }
          ]
        }""".trimIndent()

    }
}