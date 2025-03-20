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
import org.springframework.http.*
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Sinks
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

interface ConnectionCheckService {

    fun connectionAvailable(): ConnectionCheckResult

}

interface OutputConnectionCheckService : ConnectionCheckService

sealed class ConnectionCheckResult {

    abstract val available: Boolean

    abstract val timestamp: Instant

    abstract val lastChange: Instant

    data class KafkaConnectionCheckResult(
        override val available: Boolean,
        override val timestamp: Instant,
        override val lastChange: Instant
    ) : ConnectionCheckResult()

    data class RestConnectionCheckResult(
        override val available: Boolean,
        override val timestamp: Instant,
        override val lastChange: Instant
    ) : ConnectionCheckResult()

    data class GPasConnectionCheckResult(
        override val available: Boolean,
        override val timestamp: Instant,
        override val lastChange: Instant
    ) : ConnectionCheckResult()
}

class KafkaConnectionCheckService(
    private val consumer: Consumer<String, String>,
    @Qualifier("connectionCheckUpdateProducer")
    private val connectionCheckUpdateProducer: Sinks.Many<ConnectionCheckResult>
) : OutputConnectionCheckService {

    private var result = ConnectionCheckResult.KafkaConnectionCheckResult(false, Instant.now(), Instant.now())

    @PostConstruct
    @Scheduled(cron = "0 * * * * *")
    fun check() {
        result = try {
            val available = null != consumer.listTopics(5.seconds.toJavaDuration())
            ConnectionCheckResult.KafkaConnectionCheckResult(
                available,
                Instant.now(),
                if (result.available == available) { result.lastChange } else { Instant.now() }
            )
        } catch (e: TimeoutException) {
            ConnectionCheckResult.KafkaConnectionCheckResult(
                false,
                Instant.now(),
                if (!result.available) { result.lastChange } else { Instant.now() }
            )
        }
        connectionCheckUpdateProducer.emitNext(
            result,
            Sinks.EmitFailureHandler.FAIL_FAST
        )
    }

    override fun connectionAvailable(): ConnectionCheckResult.KafkaConnectionCheckResult {
        return this.result
    }

}

class RestConnectionCheckService(
    private val restTemplate: RestTemplate,
    private val restTargetProperties: RestTargetProperties,
    @Qualifier("connectionCheckUpdateProducer")
    private val connectionCheckUpdateProducer: Sinks.Many<ConnectionCheckResult>
) : OutputConnectionCheckService {

    private var result = ConnectionCheckResult.RestConnectionCheckResult(false, Instant.now(), Instant.now())

    @PostConstruct
    @Scheduled(cron = "0 * * * * *")
    fun check() {
        result = try {
            val available = restTemplate.getForEntity(
                if (restTargetProperties.isBwhc) {
                    UriComponentsBuilder.fromUriString(restTargetProperties.uri.toString()).path("").toUriString()
                } else {
                    UriComponentsBuilder.fromUriString(restTargetProperties.uri.toString())
                        .pathSegment("mtb")
                        .pathSegment("kaplan-meier")
                        .pathSegment("config")
                        .toUriString()
                },
                String::class.java
            ).statusCode == HttpStatus.OK

            ConnectionCheckResult.RestConnectionCheckResult(
                available,
                Instant.now(),
                if (result.available == available) { result.lastChange } else { Instant.now() }
            )
        } catch (e: Exception) {
            ConnectionCheckResult.RestConnectionCheckResult(
                false,
                Instant.now(),
                if (!result.available) { result.lastChange } else { Instant.now() }
            )
        }
        connectionCheckUpdateProducer.emitNext(
            result,
            Sinks.EmitFailureHandler.FAIL_FAST
        )
    }

    override fun connectionAvailable(): ConnectionCheckResult.RestConnectionCheckResult {
        return this.result
    }
}

class GPasConnectionCheckService(
    private val restTemplate: RestTemplate,
    private val gPasConfigProperties: GPasConfigProperties,
    @Qualifier("connectionCheckUpdateProducer")
    private val connectionCheckUpdateProducer: Sinks.Many<ConnectionCheckResult>
) : ConnectionCheckService {

    private var result = ConnectionCheckResult.GPasConnectionCheckResult(false, Instant.now(), Instant.now())

    @PostConstruct
    @Scheduled(cron = "0 * * * * *")
    fun check() {
        result = try {
            val uri = UriComponentsBuilder.fromUriString(
                gPasConfigProperties.uri?.replace("/\$pseudonymizeAllowCreate", "/metadata").toString()
            ).build().toUri()

            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON
            if (!gPasConfigProperties.username.isNullOrBlank() && !gPasConfigProperties.password.isNullOrBlank()) {
                headers.setBasicAuth(gPasConfigProperties.username, gPasConfigProperties.password)
            }

            val available = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                Void::class.java
            ).statusCode == HttpStatus.OK

            ConnectionCheckResult.GPasConnectionCheckResult(
                available,
                Instant.now(),
                if (result.available == available) { result.lastChange } else { Instant.now() }
            )
        } catch (e: Exception) {
            ConnectionCheckResult.GPasConnectionCheckResult(
                false,
                Instant.now(),
                if (!result.available) { result.lastChange } else { Instant.now() }
            )
        }
        connectionCheckUpdateProducer.emitNext(
            result,
            Sinks.EmitFailureHandler.FAIL_FAST
        )
    }

    override fun connectionAvailable(): ConnectionCheckResult.GPasConnectionCheckResult {
        return this.result
    }
}