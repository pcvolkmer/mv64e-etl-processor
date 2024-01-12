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
import dev.dnpm.etl.processor.monitoring.RequestRepository
import dev.dnpm.etl.processor.output.KafkaMtbFileSender
import dev.dnpm.etl.processor.output.RestMtbFileSender
import dev.dnpm.etl.processor.pseudonym.AnonymizingGenerator
import dev.dnpm.etl.processor.pseudonym.GpasPseudonymGenerator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@ContextConfiguration(classes = [AppConfiguration::class, KafkaAutoConfiguration::class, AppKafkaConfiguration::class, AppRestConfiguration::class])
@MockBean(ObjectMapper::class)
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
            "app.kafka.topic=test",
            "app.kafka.response-topic=test-response",
            "app.kafka.group-id=test"
        ]
    )
    @MockBean(RequestRepository::class)
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
            "app.kafka.topic=test",
            "app.kafka.response-topic=test-response",
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
                "app.pseudonymize.generator=",
                "app.pseudonymizer=buildin",
            ]
        )
        inner class AppConfigurationPseudonymizerBuildinTest(private val context: ApplicationContext) {

            @Test
            fun shouldUseConfiguredGenerator() {
                assertThat(context.getBean(AnonymizingGenerator::class.java)).isNotNull
            }

        }

        @Nested
        @TestPropertySource(
            properties = [
                "app.pseudonymize.generator=",
                "app.pseudonymizer=gpas",
            ]
        )
        inner class AppConfigurationPseudonymizerGpasTest(private val context: ApplicationContext) {

            @Test
            fun shouldUseConfiguredGenerator() {
                assertThat(context.getBean(GpasPseudonymGenerator::class.java)).isNotNull
            }

        }

        @Nested
        @TestPropertySource(
            properties = [
                "app.pseudonymize.generator=buildin",
                "app.pseudonymizer=",
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
                "app.pseudonymize.generator=gpas",
                "app.pseudonymizer=",
            ]
        )
        inner class AppConfigurationPseudonymizeGeneratorGpasTest(private val context: ApplicationContext) {

            @Test
            fun shouldUseConfiguredGenerator() {
                assertThat(context.getBean(GpasPseudonymGenerator::class.java)).isNotNull
            }

        }

    }

}