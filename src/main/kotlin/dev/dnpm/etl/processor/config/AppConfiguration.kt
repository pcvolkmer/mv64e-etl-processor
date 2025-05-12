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

package dev.dnpm.etl.processor.config

import com.fasterxml.jackson.databind.ObjectMapper
import dev.dnpm.etl.processor.consent.ConsentCheckFileBased
import dev.dnpm.etl.processor.consent.ICheckConsent
import dev.dnpm.etl.processor.consent.GicsConsentService
import dev.dnpm.etl.processor.monitoring.ConnectionCheckResult
import dev.dnpm.etl.processor.monitoring.ConnectionCheckService
import dev.dnpm.etl.processor.monitoring.GPasConnectionCheckService
import dev.dnpm.etl.processor.monitoring.ReportService
import dev.dnpm.etl.processor.pseudonym.AnonymizingGenerator
import dev.dnpm.etl.processor.pseudonym.Generator
import dev.dnpm.etl.processor.pseudonym.GpasPseudonymGenerator
import dev.dnpm.etl.processor.pseudonym.PseudonymizeService
import dev.dnpm.etl.processor.security.TokenRepository
import dev.dnpm.etl.processor.security.TokenService
import dev.dnpm.etl.processor.services.Transformation
import dev.dnpm.etl.processor.services.TransformationService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration
import org.springframework.retry.RetryCallback
import org.springframework.retry.RetryContext
import org.springframework.retry.RetryListener
import org.springframework.retry.policy.SimpleRetryPolicy
import org.springframework.retry.support.RetryTemplate
import org.springframework.retry.support.RetryTemplateBuilder
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import reactor.core.publisher.Sinks
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration


@Configuration
@EnableConfigurationProperties(
    value = [
        AppConfigProperties::class,
        PseudonymizeConfigProperties::class,
        GPasConfigProperties::class,
        GIcsConfigProperties::class
    ]
)
@EnableScheduling
class AppConfiguration {

    private val logger = LoggerFactory.getLogger(AppConfiguration::class.java)

    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplate()
    }

    @Bean
    fun appFhirConfig(): AppFhirConfig{
        return AppFhirConfig()
    }

    @ConditionalOnProperty(value = ["app.pseudonymize.generator"], havingValue = "GPAS")
    @Bean
    fun gpasPseudonymGenerator(configProperties: GPasConfigProperties, retryTemplate: RetryTemplate, restTemplate: RestTemplate, appFhirConfig: AppFhirConfig): Generator {
        return GpasPseudonymGenerator(configProperties, retryTemplate, restTemplate, appFhirConfig)
    }

    @ConditionalOnProperty(value = ["app.pseudonymize.generator"], havingValue = "BUILDIN", matchIfMissing = true)
    @Bean
    fun buildinPseudonymGenerator(): Generator {
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
    fun retryTemplate(configProperties: AppConfigProperties): RetryTemplate {
        return RetryTemplateBuilder()
            .notRetryOn(IllegalArgumentException::class.java)
            .notRetryOn(HttpClientErrorException.BadRequest::class.java)
            .notRetryOn(HttpClientErrorException.UnprocessableEntity::class.java)
            .exponentialBackoff(2.seconds.toJavaDuration(), 1.25, 5.seconds.toJavaDuration())
            .customPolicy(SimpleRetryPolicy(configProperties.maxRetryAttempts))
            .withListener(object : RetryListener {
                override fun <T : Any, E : Throwable> onError(
                    context: RetryContext,
                    callback: RetryCallback<T, E>,
                    throwable: Throwable
                ) {
                    logger.warn("Error occured: {}. Retrying {}", throwable.message, context.retryCount)
                }
            })
            .build()
    }

    @ConditionalOnProperty(value = ["app.security.enable-tokens"], havingValue = "true")
    @Bean
    fun tokenService(userDetailsManager: InMemoryUserDetailsManager, passwordEncoder: PasswordEncoder, tokenRepository: TokenRepository): TokenService {
        return TokenService(userDetailsManager, passwordEncoder, tokenRepository)
    }

    @Bean
    fun statisticsUpdateProducer(): Sinks.Many<Any> {
        return Sinks.many().multicast().directBestEffort()
    }

    @Bean
    fun connectionCheckUpdateProducer(): Sinks.Many<ConnectionCheckResult> {
        return Sinks.many().multicast().onBackpressureBuffer()
    }

    @ConditionalOnProperty(value = ["app.pseudonymize.generator"], havingValue = "GPAS")
    @Bean
    fun gPasConnectionCheckService(
        restTemplate: RestTemplate,
        gPasConfigProperties: GPasConfigProperties,
        connectionCheckUpdateProducer: Sinks.Many<ConnectionCheckResult>
    ): ConnectionCheckService {
        return GPasConnectionCheckService(restTemplate, gPasConfigProperties, connectionCheckUpdateProducer)
    }

    @ConditionalOnProperty(value = ["app.pseudonymizer"], havingValue = "GPAS")
    @ConditionalOnMissingBean
    @Bean
    fun gPasConnectionCheckServiceOnDeprecatedProperty(
        restTemplate: RestTemplate,
        gPasConfigProperties: GPasConfigProperties,
        connectionCheckUpdateProducer: Sinks.Many<ConnectionCheckResult>
    ): ConnectionCheckService {
        return GPasConnectionCheckService(restTemplate, gPasConfigProperties, connectionCheckUpdateProducer)
    }

    @Bean
    fun jdbcConfiguration(): AbstractJdbcConfiguration {
        return AppJdbcConfiguration()
    }

    @Bean
    @ConditionalOnMissingBean
    fun constService(): ICheckConsent {
        return ConsentCheckFileBased()
    }

    @Bean
    @ConditionalOnProperty(name = ["app.consent.gics.enabled"], havingValue = "true")
    fun gicsAccessConsent( gIcsConfigProperties: GIcsConfigProperties,
                           retryTemplate: RetryTemplate,  restTemplate: RestTemplate,  appFhirConfig: AppFhirConfig): ICheckConsent {
        return GicsConsentService(
            gIcsConfigProperties,
            retryTemplate,
            restTemplate,
            appFhirConfig
        )
    }
}

