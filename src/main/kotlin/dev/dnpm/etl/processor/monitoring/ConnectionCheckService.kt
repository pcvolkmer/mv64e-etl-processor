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

import dev.dnpm.etl.processor.config.GPasConfigProperties
import dev.dnpm.etl.processor.config.RestTargetProperties
import jakarta.annotation.PostConstruct
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.common.errors.TimeoutException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Sinks
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

interface ConnectionCheckService {

    fun connectionAvailable(): Boolean

}

interface OutputConnectionCheckService : ConnectionCheckService

sealed class ConnectionCheckResult {

    abstract val available: Boolean

    data class KafkaConnectionCheckResult(override val available: Boolean) : ConnectionCheckResult()
    data class RestConnectionCheckResult(override val available: Boolean) : ConnectionCheckResult()
    data class GPasConnectionCheckResult(override val available: Boolean) : ConnectionCheckResult()
}

class KafkaConnectionCheckService(
    private val consumer: Consumer<String, String>,
    @Qualifier("connectionCheckUpdateProducer")
    private val connectionCheckUpdateProducer: Sinks.Many<ConnectionCheckResult>
) : OutputConnectionCheckService {

    private var connectionAvailable: Boolean = false


    @PostConstruct
    @Scheduled(cron = "0 * * * * *")
    fun check() {
        connectionAvailable = try {
            null != consumer.listTopics(5.seconds.toJavaDuration())
        } catch (e: TimeoutException) {
            false
        }
        connectionCheckUpdateProducer.emitNext(
            ConnectionCheckResult.KafkaConnectionCheckResult(connectionAvailable),
            Sinks.EmitFailureHandler.FAIL_FAST
        )
    }

    override fun connectionAvailable(): Boolean {
        return this.connectionAvailable
    }

}

class RestConnectionCheckService(
    private val restTemplate: RestTemplate,
    private val restTargetProperties: RestTargetProperties,
    @Qualifier("connectionCheckUpdateProducer")
    private val connectionCheckUpdateProducer: Sinks.Many<ConnectionCheckResult>
) : OutputConnectionCheckService {

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
        connectionCheckUpdateProducer.emitNext(
            ConnectionCheckResult.RestConnectionCheckResult(connectionAvailable),
            Sinks.EmitFailureHandler.FAIL_FAST
        )
    }

    override fun connectionAvailable(): Boolean {
        return this.connectionAvailable
    }
}

class GPasConnectionCheckService(
    private val restTemplate: RestTemplate,
    private val gPasConfigProperties: GPasConfigProperties,
    @Qualifier("connectionCheckUpdateProducer")
    private val connectionCheckUpdateProducer: Sinks.Many<ConnectionCheckResult>
) : ConnectionCheckService {

    private var connectionAvailable: Boolean = false

    @PostConstruct
    @Scheduled(cron = "0 * * * * *")
    fun check() {
        connectionAvailable = try {
            val uri = UriComponentsBuilder.fromUriString(
                gPasConfigProperties.uri?.replace("/\$pseudonymizeAllowCreate", "/\$pseudonymize").toString()
            )
                .queryParam("target", gPasConfigProperties.target)
                .queryParam("original", "???")
                .build().toUri()

            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON
            if (!gPasConfigProperties.username.isNullOrBlank() && !gPasConfigProperties.password.isNullOrBlank()) {
                headers.setBasicAuth(gPasConfigProperties.username, gPasConfigProperties.password)
            }
            restTemplate.exchange(
                uri,
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                Void::class.java
            ).statusCode == HttpStatus.OK
        } catch (e: Exception) {
            false
        }
        connectionCheckUpdateProducer.emitNext(
            ConnectionCheckResult.GPasConnectionCheckResult(connectionAvailable),
            Sinks.EmitFailureHandler.FAIL_FAST
        )
    }

    override fun connectionAvailable(): Boolean {
        return this.connectionAvailable
    }
}