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
import dev.dnpm.etl.processor.monitoring.ReportService
import dev.dnpm.etl.processor.pseudonym.AnonymizingGenerator
import dev.dnpm.etl.processor.pseudonym.Generator
import dev.dnpm.etl.processor.pseudonym.GpasPseudonymGenerator
import dev.dnpm.etl.processor.pseudonym.PseudonymizeService
import dev.dnpm.etl.processor.services.Transformation
import dev.dnpm.etl.processor.services.TransformationService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.retry.policy.SimpleRetryPolicy
import org.springframework.retry.support.RetryTemplate
import org.springframework.retry.support.RetryTemplateBuilder
import org.springframework.scheduling.annotation.EnableScheduling
import reactor.core.publisher.Sinks
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration


@Configuration
@EnableConfigurationProperties(
    value = [
        AppConfigProperties::class,
        PseudonymizeConfigProperties::class,
        GPasConfigProperties::class
    ]
)
@EnableScheduling
class AppConfiguration {

    private val logger = LoggerFactory.getLogger(AppConfiguration::class.java)

    @ConditionalOnProperty(value = ["app.pseudonymize.generator"], havingValue = "GPAS")
    @Bean
    fun gpasPseudonymGenerator(configProperties: GPasConfigProperties): Generator {
        return GpasPseudonymGenerator(configProperties)
    }

    @ConditionalOnProperty(value = ["app.pseudonymize.generator"], havingValue = "BUILDIN")
    @Bean
    fun buildinPseudonymGenerator(): Generator {
        return AnonymizingGenerator()
    }

    @ConditionalOnProperty(value = ["app.pseudonymizer"], havingValue = "GPAS")
    @ConditionalOnMissingBean
    @Bean
    fun gpasPseudonymGeneratorOnDeprecatedProperty(configProperties: GPasConfigProperties): Generator {
        return GpasPseudonymGenerator(configProperties)
    }

    @ConditionalOnProperty(value = ["app.pseudonymizer"], havingValue = "BUILDIN")
    @ConditionalOnMissingBean
    @Bean
    fun buildinPseudonymGeneratorOnDeprecatedProperty(): Generator {
        return AnonymizingGenerator()
    }

    @Bean
    fun pseudonymizeService(
        generator: Generator,
        pseudonymizeConfigProperties: PseudonymizeConfigProperties
    ): PseudonymizeService {
        return PseudonymizeService(generator, pseudonymizeConfigProperties)
    }

    @Bean
    fun reportService(objectMapper: ObjectMapper): ReportService {
        return ReportService(objectMapper)
    }

    @Bean
    fun statisticsUpdateProducer(): Sinks.Many<Any> {
        return Sinks.many().multicast().directBestEffort()
    }

    @Bean
    fun transformationService(
        objectMapper: ObjectMapper,
        configProperties: AppConfigProperties
    ): TransformationService {
        logger.info("Apply ${configProperties.transformations.size} transformation rules")
        return TransformationService(objectMapper, configProperties.transformations.map {
            Transformation.of(it.path) from it.from to it.to
        })
    }

    @Bean
    fun retryTemplate(): RetryTemplate {
        return RetryTemplateBuilder()
            .notRetryOn(IllegalArgumentException::class.java)
            .fixedBackoff(5.seconds.toJavaDuration())
            .customPolicy(SimpleRetryPolicy(3))
            .build()
    }

}

