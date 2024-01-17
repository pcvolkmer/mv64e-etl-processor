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


package dev.dnpm.etl.processor.monitoring

import dev.dnpm.etl.processor.config.RestTargetProperties
import jakarta.annotation.PostConstruct
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.common.errors.TimeoutException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.client.RestTemplate
import reactor.core.publisher.Sinks
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

interface ConnectionCheckService {

    fun connectionAvailable(): Boolean

}

class KafkaConnectionCheckService(
    private val consumer: Consumer<String, String>,
    @Qualifier("configsUpdateProducer")
    private val configsUpdateProducer: Sinks.Many<Boolean>
) : ConnectionCheckService {

    private var connectionAvailable: Boolean = false


    @PostConstruct
    @Scheduled(cron = "0 * * * * *")
    fun check() {
        connectionAvailable = try {
            null != consumer.listTopics(5.seconds.toJavaDuration())
        } catch (e: TimeoutException) {
            false
        }
        configsUpdateProducer.emitNext(connectionAvailable, Sinks.EmitFailureHandler.FAIL_FAST)
    }

    override fun connectionAvailable(): Boolean {
        return this.connectionAvailable
    }

}

class RestConnectionCheckService(
    private val restTemplate: RestTemplate,
    private val restTargetProperties: RestTargetProperties,
    @Qualifier("configsUpdateProducer")
    private val configsUpdateProducer: Sinks.Many<Boolean>
) : ConnectionCheckService {

    private var connectionAvailable: Boolean = false

    @PostConstruct
    @Scheduled(cron = "0 * * * * *")
    fun check() {
        connectionAvailable = try {
            restTemplate.getForEntity(
                restTargetProperties.uri?.replace("/etl/api", "").toString(),
                String::class.java
            ).statusCode == HttpStatus.OK
        } catch (e: Exception) {
            false
        }
        configsUpdateProducer.emitNext(connectionAvailable, Sinks.EmitFailureHandler.FAIL_FAST)
    }

    override fun connectionAvailable(): Boolean {
        return this.connectionAvailable
    }
}