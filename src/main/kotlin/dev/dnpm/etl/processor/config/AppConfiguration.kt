/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2023  Comprehensive Cancer Center Mainfranken, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Sinks

@Configuration
@EnableConfigurationProperties(
    value = [
        AppConfigProperties::class,
        PseudonymizeConfigProperties::class,
        GPasConfigProperties::class
    ]
)
class AppConfiguration {

    @ConditionalOnProperty(value = ["app.pseudonymizer"], havingValue = "GPAS")
    @Bean
    fun gpasPseudonymGenerator(configProperties: GPasConfigProperties): Generator {
        return GpasPseudonymGenerator(configProperties)
    }

    @ConditionalOnProperty(value = ["app.pseudonymizer"], havingValue = "BUILDIN", matchIfMissing = true)
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
    fun statisticsUpdateProducer(): Sinks.Many<Any> {
        return Sinks.many().multicast().directBestEffort()
    }

    @Bean
    fun transformationService(
        objectMapper: ObjectMapper,
        configProperties: AppConfigProperties
    ): TransformationService {
        return TransformationService(objectMapper, configProperties.transformations.map {
            Transformation.of(it.path) from it.from to it.to
        })
    }

}

