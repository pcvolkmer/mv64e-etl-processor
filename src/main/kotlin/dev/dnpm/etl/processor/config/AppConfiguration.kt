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
import dev.dnpm.etl.processor.consent.GicsConsentService
import dev.dnpm.etl.processor.consent.IConsentService
import dev.dnpm.etl.processor.consent.MtbFileConsentService
import dev.dnpm.etl.processor.monitoring.*
import dev.dnpm.etl.processor.pseudonym.*
import dev.dnpm.etl.processor.security.TokenRepository
import dev.dnpm.etl.processor.security.TokenService
import dev.dnpm.etl.processor.services.ConsentProcessor
import dev.dnpm.etl.processor.services.Transformation
import dev.dnpm.etl.processor.services.TransformationService
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ConfigurationCondition
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
        ConsentConfigProperties::class,
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
    fun appFhirConfig(): AppFhirConfig {
        return AppFhirConfig()
    }

    @ConditionalOnProperty(value = ["app.pseudonymize.generator"], havingValue = "GPAS")
    @ConditionalOnProperty(value = ["app.pseudonymize.gpas.soap-endpoint"])
    @Bean
    fun gpasSoapProxyFactoryBean(gpasConfigProperties: GPasConfigProperties): JaxWsProxyFactoryBean {
        val proxyFactory = JaxWsProxyFactoryBean()
        proxyFactory.serviceClass = GpasSoapService::class.java
        proxyFactory.address = gpasConfigProperties.soapEndpoint
        return proxyFactory
    }

    @ConditionalOnProperty(value = ["app.pseudonymize.generator"], havingValue = "GPAS")
    @ConditionalOnProperty(value = ["app.pseudonymize.gpas.soap-endpoint"])
    @Bean
    fun gpasSoapProxy(gpasConfigProperties: GPasConfigProperties): GpasSoapService {
        return gpasSoapProxyFactoryBean(gpasConfigProperties).create() as GpasSoapService
    }

    @ConditionalOnProperty(value = ["app.pseudonymize.generator"], havingValue = "GPAS")
    @ConditionalOnProperty(value = ["app.pseudonymize.gpas.soap-endpoint"])
    @Bean
    fun gpasSoapPseudonymGenerator(
        configProperties: GPasConfigProperties,
        retryTemplate: RetryTemplate,
        gpasSoapService: GpasSoapService,
        appFhirConfig: AppFhirConfig
    ): Generator {
        logger.info("Selected 'GpasSoapPseudonym Generator'")
        return GpasSoapPseudonymGenerator(configProperties, retryTemplate, gpasSoapService, appFhirConfig)
    }

    @ConditionalOnProperty(value = ["app.pseudonymize.generator"], havingValue = "GPAS")
    @ConditionalOnProperty(value = ["app.pseudonymize.gpas.uri"])
    @Bean
    fun gpasPseudonymGenerator(
        configProperties: GPasConfigProperties,
        retryTemplate: RetryTemplate,
        restTemplate: RestTemplate,
        appFhirConfig: AppFhirConfig
    ): Generator {
        logger.info("Selected 'GpasPseudonym Generator'")
        return GpasPseudonymGenerator(configProperties, retryTemplate, restTemplate, appFhirConfig)
    }

    @ConditionalOnProperty(
        value = ["app.pseudonymize.generator"],
        havingValue = "BUILDIN",
        matchIfMissing = true
    )
    @Bean
    fun buildinPseudonymGenerator(): Generator {
        logger.info("Selected 'BUILDIN Pseudonym Generator'")
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
    fun reportService(): ReportService {
        return ReportService(getObjectMapper())
    }

    @Bean
    fun getObjectMapper(): ObjectMapper {
        return JacksonConfig().objectMapper()
    }

    @Bean
    fun transformationService(
        configProperties: AppConfigProperties
    ): TransformationService {
        logger.info("Apply ${configProperties.transformations.size} transformation rules")
        return TransformationService(getObjectMapper(), configProperties.transformations.map {
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
                    logger.warn(
                        "Error occured: {}. Retrying {}",
                        throwable.message,
                        context.retryCount
                    )
                }
            })
            .build()
    }

    @ConditionalOnProperty(value = ["app.security.enable-tokens"], havingValue = "true")
    @Bean
    fun tokenService(
        userDetailsManager: InMemoryUserDetailsManager,
        passwordEncoder: PasswordEncoder,
        tokenRepository: TokenRepository
    ): TokenService {
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
        return GPasConnectionCheckService(
            restTemplate,
            gPasConfigProperties,
            connectionCheckUpdateProducer
        )
    }

    @ConditionalOnProperty(value = ["app.pseudonymizer"], havingValue = "GPAS")
    @ConditionalOnMissingBean
    @Bean
    fun gPasConnectionCheckServiceOnDeprecatedProperty(
        restTemplate: RestTemplate,
        gPasConfigProperties: GPasConfigProperties,
        connectionCheckUpdateProducer: Sinks.Many<ConnectionCheckResult>
    ): ConnectionCheckService {
        return GPasConnectionCheckService(
            restTemplate,
            gPasConfigProperties,
            connectionCheckUpdateProducer
        )
    }

    @Bean
    fun jdbcConfiguration(): AbstractJdbcConfiguration {
        return AppJdbcConfiguration()
    }

    @Conditional(GicsEnabledCondition::class)
    @Bean
    fun gicsConsentService(
        gIcsConfigProperties: GIcsConfigProperties,
        retryTemplate: RetryTemplate,
        restTemplate: RestTemplate,
        appFhirConfig: AppFhirConfig
    ): IConsentService {
        return GicsConsentService(
            gIcsConfigProperties,
            retryTemplate,
            restTemplate,
            appFhirConfig
        )
    }

    @Conditional(GicsEnabledCondition::class)
    @Bean
    fun consentProcessor(
        configProperties: AppConfigProperties,
        gIcsConfigProperties: GIcsConfigProperties,
        getObjectMapper: ObjectMapper,
        appFhirConfig: AppFhirConfig,
        gicsConsentService: IConsentService
    ): ConsentProcessor {
        return ConsentProcessor(
            configProperties,
            gIcsConfigProperties,
            getObjectMapper,
            appFhirConfig.fhirContext(),
            gicsConsentService
        )
    }

    @Conditional(GicsEnabledCondition::class)
    @Bean
    fun gIcsConnectionCheckService(
        restTemplate: RestTemplate,
        gIcsConfigProperties: GIcsConfigProperties,
        connectionCheckUpdateProducer: Sinks.Many<ConnectionCheckResult>
    ): ConnectionCheckService {
        return GIcsConnectionCheckService(
            restTemplate,
            gIcsConfigProperties,
            connectionCheckUpdateProducer
        )
    }

    @Bean
    @ConditionalOnMissingBean
    fun iGetConsentService(): IConsentService {
        return MtbFileConsentService()
    }

}

class GicsEnabledCondition :
    AnyNestedCondition(ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN) {

    @ConditionalOnProperty(name = ["app.consent.service"], havingValue = "gics")
    @ConditionalOnProperty(name = ["app.consent.gics.uri"])
    class OnGicsServiceSelected {
        // Just for Condition
    }

}
