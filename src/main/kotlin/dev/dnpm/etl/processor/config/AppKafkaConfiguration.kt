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
import dev.dnpm.etl.processor.input.KafkaInputListener
import dev.dnpm.etl.processor.monitoring.ConnectionCheckService
import dev.dnpm.etl.processor.monitoring.KafkaConnectionCheckService
import dev.dnpm.etl.processor.output.KafkaMtbFileSender
import dev.dnpm.etl.processor.output.MtbFileSender
import dev.dnpm.etl.processor.services.RequestProcessor
import dev.dnpm.etl.processor.services.kafka.KafkaResponseProcessor
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.KafkaMessageListenerContainer
import org.springframework.retry.support.RetryTemplate
import reactor.core.publisher.Sinks

@Configuration
@EnableConfigurationProperties(
    value = [KafkaProperties::class]
)
@ConditionalOnProperty(value = ["app.kafka.servers"])
@ConditionalOnMissingBean(MtbFileSender::class)
@Order(-5)
class AppKafkaConfiguration {

    private val logger = LoggerFactory.getLogger(AppKafkaConfiguration::class.java)

    @Bean
    fun kafkaMtbFileSender(
        kafkaTemplate: KafkaTemplate<String, String>,
        kafkaProperties: KafkaProperties,
        retryTemplate: RetryTemplate,
        objectMapper: ObjectMapper
    ): MtbFileSender {
        logger.info("Selected 'KafkaMtbFileSender'")
        return KafkaMtbFileSender(kafkaTemplate, kafkaProperties, retryTemplate, objectMapper)
    }

    @Bean
    fun kafkaResponseListenerContainer(
        consumerFactory: ConsumerFactory<String, String>,
        kafkaProperties: KafkaProperties,
        kafkaResponseProcessor: KafkaResponseProcessor
    ): KafkaMessageListenerContainer<String, String> {
        val containerProperties = ContainerProperties(kafkaProperties.responseTopic)
        containerProperties.messageListener = kafkaResponseProcessor
        return KafkaMessageListenerContainer(consumerFactory, containerProperties)
    }

    @Bean
    fun kafkaResponseProcessor(
        applicationEventPublisher: ApplicationEventPublisher,
        objectMapper: ObjectMapper
    ): KafkaResponseProcessor {
        return KafkaResponseProcessor(applicationEventPublisher, objectMapper)
    }

    @Bean
    @ConditionalOnProperty(value = ["app.kafka.input-topic"])
    fun kafkaInputListenerContainer(
        consumerFactory: ConsumerFactory<String, String>,
        kafkaProperties: KafkaProperties,
        kafkaInputListener: KafkaInputListener
    ): KafkaMessageListenerContainer<String, String> {
        val containerProperties = ContainerProperties(kafkaProperties.inputTopic)
        containerProperties.messageListener = kafkaInputListener
        return KafkaMessageListenerContainer(consumerFactory, containerProperties)
    }

    @Bean
    @ConditionalOnProperty(value = ["app.kafka.input-topic"])
    fun kafkaInputListener(
        requestProcessor: RequestProcessor
    ): KafkaInputListener {
        return KafkaInputListener(requestProcessor)
    }

    @Bean
    fun connectionCheckService(consumerFactory: ConsumerFactory<String, String>, configsUpdateProducer: Sinks.Many<Boolean>): ConnectionCheckService {
        return KafkaConnectionCheckService(consumerFactory.createConsumer(), configsUpdateProducer)
    }

}