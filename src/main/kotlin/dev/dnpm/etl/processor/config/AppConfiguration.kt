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
import dev.dnpm.etl.processor.output.KafkaMtbFileSender
import dev.dnpm.etl.processor.output.MtbFileSender
import dev.dnpm.etl.processor.output.RestMtbFileSender
import dev.dnpm.etl.processor.pseudonym.AnonymizingGenerator
import dev.dnpm.etl.processor.pseudonym.Generator
import dev.dnpm.etl.processor.pseudonym.GpasPseudonymGenerator
import dev.dnpm.etl.processor.pseudonym.PseudonymizeService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate
import java.net.URI

@Configuration
@EnableConfigurationProperties(
    value = [
        AppConfigProperties::class,
        PseudonymizeConfigProperties::class,
        GPasConfigProperties::class,
        RestTargetProperties::class,
        KafkaTargetProperties::class
    ]
)
class AppConfiguration {

    @ConditionalOnProperty(value = ["app.pseudonymizer"], havingValue = "GPAS")
    @Bean
    fun gpasPseudonymGenerator(configProperties: GPasConfigProperties): Generator {
        return GpasPseudonymGenerator(URI.create(configProperties.uri!!), configProperties.target)
    }

    @ConditionalOnProperty(value = ["app.pseudonymizer"], havingValue = "BUILDIN", matchIfMissing = true)
    @Bean
    fun buildinPseudonymGenerator(): Generator {
        return AnonymizingGenerator()
    }

    @Bean
    fun pseudonymizeService(generator: Generator, pseudonymizeConfigProperties: PseudonymizeConfigProperties): PseudonymizeService {
        return PseudonymizeService(generator, pseudonymizeConfigProperties)
    }

    @ConditionalOnProperty(value = ["app.rest.uri"])
    @Bean
    fun restMtbFileSender(restTargetProperties: RestTargetProperties): MtbFileSender {
        return RestMtbFileSender(restTargetProperties)
    }

    @ConditionalOnProperty(value = ["app.kafka.topic", "app.kafka.servers"])
    @Bean
    fun kafkaMtbFileSender(
        kafkaTemplate: KafkaTemplate<String, String>,
        objectMapper: ObjectMapper
    ): MtbFileSender {
        return KafkaMtbFileSender(kafkaTemplate, objectMapper)
    }

}

