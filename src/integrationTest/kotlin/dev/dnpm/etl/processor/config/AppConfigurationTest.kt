/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2024  Comprehensive Cancer Center Mainfranken, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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

package dev.dnpm.etl.processor.config

import com.fasterxml.jackson.databind.ObjectMapper
import dev.dnpm.etl.processor.consent.ConsentEvaluator
import dev.dnpm.etl.processor.consent.GicsConsentService
import dev.dnpm.etl.processor.consent.MtbFileConsentService
import dev.dnpm.etl.processor.input.KafkaInputListener
import dev.dnpm.etl.processor.monitoring.RequestRepository
import dev.dnpm.etl.processor.output.KafkaMtbFileSender
import dev.dnpm.etl.processor.output.RestMtbFileSender
import dev.dnpm.etl.processor.pseudonym.AnonymizingGenerator
import dev.dnpm.etl.processor.pseudonym.GpasPseudonymGenerator
import dev.dnpm.etl.processor.security.TokenRepository
import dev.dnpm.etl.processor.security.TokenService
import dev.dnpm.etl.processor.services.RequestProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.retry.support.RetryTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest
@ContextConfiguration(
    classes = [
        AppConfiguration::class,
        AppSecurityConfiguration::class,
        KafkaAutoConfiguration::class,
        AppKafkaConfiguration::class,
        AppRestConfiguration::class,
        ConsentEvaluator::class
    ]
)
@MockitoBean(types = [ObjectMapper::class])
@TestPropertySource(
    properties = [
        "app.pseudonymize.generator=BUILDIN",
    ]
)
class AppConfigurationTest {

    @Nested
    @TestPropertySource(
        properties = [
            "app.rest.uri=http://localhost:9000"
        ]
    )
    inner class AppConfigurationRestTest(private val context: ApplicationContext) {

        @Test
        fun shouldUseRestMtbFileSenderNotKafkaMtbFileSender() {
            assertThat(context.getBean(RestMtbFileSender::class.java)).isNotNull
            assertThrows<NoSuchBeanDefinitionException> { context.getBean(KafkaMtbFileSender::class.java) }
        }

    }

    @Nested
    @TestPropertySource(
        properties = [
            "app.kafka.servers=localhost:9092",
            "app.kafka.output-topic=test",
            "app.kafka.output-response-topic=test-response",
            "app.kafka.group-id=test"
        ]
    )
    @MockitoBean(types = [RequestRepository::class])
    inner class AppConfigurationKafkaTest(private val context: ApplicationContext) {

        @Test
        fun shouldUseKafkaMtbFileSenderNotRestMtbFileSender() {
            assertThrows<NoSuchBeanDefinitionException> { context.getBean(RestMtbFileSender::class.java) }
            assertThat(context.getBean(KafkaMtbFileSender::class.java)).isNotNull
        }

    }

    @Nested
    @TestPropertySource(
        properties = [
            "app.rest.uri=http://localhost:9000",
            "app.kafka.servers=localhost:9092",
            "app.kafka.output-topic=test",
            "app.kafka.output-response-topic=test-response",
            "app.kafka.group-id=test"
        ]
    )
    inner class AppConfigurationRestInPrecedenceTest(private val context: ApplicationContext) {

        @Test
        fun shouldUseRestMtbFileSenderNotKafkaMtbFileSender() {
            assertThat(context.getBean(RestMtbFileSender::class.java)).isNotNull
            assertThrows<NoSuchBeanDefinitionException> { context.getBean(KafkaMtbFileSender::class.java) }
        }

    }

    @Nested
    @TestPropertySource(
        properties = [
            "app.kafka.servers=localhost:9092",
            "app.kafka.output-topic=test",
            "app.kafka.output-response-topic=test-response",
            "app.kafka.group-id=test"
        ]
    )
    inner class AppConfigurationWithoutKafkaInputTest(private val context: ApplicationContext) {

        @Test
        fun shouldNotUseKafkaInputListener() {
            assertThrows<NoSuchBeanDefinitionException> { context.getBean(KafkaInputListener::class.java) }
        }

    }

    @Nested
    @TestPropertySource(
        properties = [
            "app.kafka.servers=localhost:9092",
            "app.kafka.input-topic=test_input",
            "app.kafka.output-topic=test",
            "app.kafka.output-response-topic=test-response",
            "app.kafka.group-id=test"
        ]
    )
    @MockitoBean(types = [RequestProcessor::class])
    inner class AppConfigurationUsingKafkaInputTest(private val context: ApplicationContext) {

        @Test
        fun shouldUseKafkaInputListener() {
            assertThat(context.getBean(KafkaInputListener::class.java)).isNotNull
        }

    }

    @Nested
    @TestPropertySource(
        properties = [
            "app.transformations[0].path=consent.status",
            "app.transformations[0].from=rejected",
            "app.transformations[0].to=accept",
        ]
    )
    inner class AppConfigurationTransformationTest(private val context: ApplicationContext) {

        @Test
        fun shouldRecognizeTransformations() {
            val appConfigProperties = context.getBean(AppConfigProperties::class.java)

            assertThat(appConfigProperties).isNotNull
            assertThat(appConfigProperties.transformations).hasSize(1)
        }

    }

    @Nested
    inner class AppConfigurationPseudonymizeTest {

        @Nested
        @TestPropertySource(
            properties = [
                "app.pseudonymize.generator=buildin"
            ]
        )
        inner class AppConfigurationPseudonymizeGeneratorBuildinTest(private val context: ApplicationContext) {

            @Test
            fun shouldUseConfiguredGenerator() {
                assertThat(context.getBean(AnonymizingGenerator::class.java)).isNotNull
            }

        }

        @Nested
        @TestPropertySource(
            properties = [
                "app.pseudonymize.generator=gpas"
            ]
        )
        inner class AppConfigurationPseudonymizeGeneratorGpasTest(private val context: ApplicationContext) {

            @Test
            fun shouldUseConfiguredGenerator() {
                assertThat(context.getBean(GpasPseudonymGenerator::class.java)).isNotNull
            }

        }

        @Nested
        @TestPropertySource(
            properties = [
                "app.security.enable-tokens=true"
            ]
        )
        @MockitoBean(
            types = [
                InMemoryUserDetailsManager::class,
                PasswordEncoder::class,
                TokenRepository::class
            ]
        )
        inner class AppConfigurationTokenEnabledTest(private val context: ApplicationContext) {

            @Test
            fun checkTokenService() {
                assertThat(context.getBean(TokenService::class.java)).isNotNull
            }

        }

        @Nested
        @MockitoBean(
            types = [
                InMemoryUserDetailsManager::class,
                PasswordEncoder::class,
                TokenRepository::class
            ]
        )
        inner class AppConfigurationTokenDisabledTest(private val context: ApplicationContext) {

            @Test
            fun checkTokenService() {
                assertThrows<NoSuchBeanDefinitionException> { context.getBean(TokenService::class.java) }
            }

        }

    }

    @Nested
    @TestPropertySource(
        properties = [
            "app.rest.uri=http://localhost:9000",
            "app.max-retry-attempts=5"
        ]
    )
    inner class AppConfigurationRetryTest(private val context: ApplicationContext) {

        private val maxRetryAttempts = 5

        @Test
        fun shouldUseRetryTemplateWithConfiguredMaxAttempts() {
            val retryTemplate = context.getBean(RetryTemplate::class.java)
            assertThat(retryTemplate).isNotNull

            assertThrows<RuntimeException> {
                retryTemplate.execute<Void, RuntimeException> {
                    assertThat(it.retryCount).isLessThan(maxRetryAttempts)
                    throw RuntimeException()
                }
            }
        }

    }

    @Nested
    @TestPropertySource(
        properties = [
            "app.consent.service=GICS",
            "app.consent.gics.uri=http://localhost:9000",
        ]
    )
    inner class AppConfigurationConsentGicsTest(private val context: ApplicationContext) {

        @Test
        fun shouldUseConfiguredGenerator() {
            assertThat(context.getBean(GicsConsentService::class.java)).isNotNull
        }

    }

    @Nested
    inner class AppConfigurationConsentBuildinTest(private val context: ApplicationContext) {

        @Test
        fun shouldUseConfiguredGenerator() {
            assertThat(context.getBean(MtbFileConsentService::class.java)).isNotNull
        }

    }

}
