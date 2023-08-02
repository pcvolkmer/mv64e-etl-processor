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
import dev.dnpm.etl.processor.monitoring.RequestRepository
import dev.dnpm.etl.processor.output.KafkaMtbFileSender
import dev.dnpm.etl.processor.output.MtbFileSender
import dev.dnpm.etl.processor.services.kafka.KafkaResponseProcessor
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.KafkaMessageListenerContainer

@Configuration
@EnableConfigurationProperties(
    value = [KafkaTargetProperties::class]
)
@ConditionalOnProperty(value = ["app.kafka.topic", "app.kafka.servers"])
class AppKafkaConfiguration {

    @Bean
    fun kafkaMtbFileSender(
        kafkaTemplate: KafkaTemplate<String, String>,
        objectMapper: ObjectMapper
    ): MtbFileSender {
        return KafkaMtbFileSender(kafkaTemplate, objectMapper)
    }

    @Bean
    fun kafkaListenerContainer(
        consumerFactory: ConsumerFactory<String, String>,
        kafkaTargetProperties: KafkaTargetProperties,
        kafkaResponseProcessor: KafkaResponseProcessor
    ): KafkaMessageListenerContainer<String, String> {
        val containerProperties = ContainerProperties(kafkaTargetProperties.responseTopic);
        containerProperties.messageListener = kafkaResponseProcessor
        return KafkaMessageListenerContainer(consumerFactory, containerProperties)
    }

    @Bean
    fun kafkaResponseProcessor(
        requestRepository: RequestRepository,
        objectMapper: ObjectMapper
    ): KafkaResponseProcessor {
        return KafkaResponseProcessor(requestRepository, objectMapper)
    }

}