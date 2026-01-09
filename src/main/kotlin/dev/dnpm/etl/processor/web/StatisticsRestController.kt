/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2023  Comprehensive Cancer Center Mainfranken
 * Copyright (c) 2023-2026  Paul-Christian Volkmer, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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

package dev.dnpm.etl.processor.web

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.dnpm.etl.processor.monitoring.RequestType
import dev.dnpm.etl.processor.services.RequestService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@RestController
@RequestMapping(path = ["/statistics"])
class StatisticsRestController(
    @param:Qualifier("statisticsUpdateProducer")
    private val statisticsUpdateProducer: Sinks.Many<Any>,
    private val requestService: RequestService,
) {
    private fun statusColor(status: RequestStatus) =
        when (status) {
            RequestStatus.ERROR -> "#FF0000"
            RequestStatus.WARNING -> "#FF8C00"
            RequestStatus.SUCCESS -> "#008000"
            RequestStatus.NO_CONSENT -> "#004A9D"
            else -> "#708090"
        }

    @GetMapping(path = ["requeststates"])
    fun requestStates(
        @RequestParam(required = false, defaultValue = "false") delete: Boolean,
    ): List<NameValue> {
        val states =
            if (delete) {
                requestService.countDeleteStates()
            } else {
                requestService.countStates()
            }

        return states
            .map {
                NameValue(it.status.toString(), it.count, statusColor(it.status))
            }.sortedByDescending { it.value }
    }

    @GetMapping(path = ["requestslastmonth"])
    fun requestsLastMonth(
        @RequestParam(required = false, defaultValue = "false") delete: Boolean,
    ): List<DateNameValues> {
        val requestType =
            if (delete) {
                RequestType.DELETE
            } else {
                RequestType.MTB_FILE
            }

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("Europe/Berlin"))
        val data =
            requestService
                .findAll()
                .filter { it.type == requestType }
                .filter { it.processedAt.isAfter(Instant.now().minus(30, ChronoUnit.DAYS)) }
                .groupBy { formatter.format(it.processedAt) }
                .map {
                    val requestList =
                        it.value
                            .groupBy { request -> request.status }
                            .map { request -> Pair(request.key, request.value.size) }
                            .toMap()
                    Pair(
                        it.key.toString(),
                        DateNameValues(
                            it.key.toString(),
                            NameValues(
                                error = requestList[RequestStatus.ERROR] ?: 0,
                                warning = requestList[RequestStatus.WARNING] ?: 0,
                                success = requestList[RequestStatus.SUCCESS] ?: 0,
                                noConsent = requestList[RequestStatus.NO_CONSENT] ?: 0,
                                duplication = requestList[RequestStatus.DUPLICATION] ?: 0,
                                blockedInitial = requestList[RequestStatus.BLOCKED_INITIAL] ?: 0,
                                unknown = requestList[RequestStatus.UNKNOWN] ?: 0,
                            ),
                        ),
                    )
                }.toMap()

        return (0L..30L)
            .map { Instant.now().minus(it, ChronoUnit.DAYS) }
            .map { formatter.format(it) }
            .map { DateNameValues(it, data[it]?.nameValues ?: NameValues()) }
            .sortedBy { it.date }
    }

    @GetMapping(path = ["requestpatientstates"])
    fun requestPatientStates(
        @RequestParam(required = false, defaultValue = "false") delete: Boolean,
    ): List<NameValue> {
        val states =
            if (delete) {
                requestService.findPatientUniqueDeleteStates()
            } else {
                requestService.findPatientUniqueStates()
            }

        return states.map {
            NameValue(it.status.toString(), it.count, statusColor(it.status))
        }
    }

    @GetMapping(path = ["events"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun updater(): Flux<ServerSentEvent<Any>> =
        statisticsUpdateProducer.asFlux().flatMap {
            Flux.fromIterable(
                listOf(
                    ServerSentEvent
                        .builder<Any>()
                        .event("requeststates")
                        .id("none")
                        .data(this.requestStates(false))
                        .build(),
                    ServerSentEvent
                        .builder<Any>()
                        .event("requestslastmonth")
                        .id("none")
                        .data(this.requestsLastMonth(false))
                        .build(),
                    ServerSentEvent
                        .builder<Any>()
                        .event("requestpatientstates")
                        .id("none")
                        .data(this.requestPatientStates(false))
                        .build(),
                    ServerSentEvent
                        .builder<Any>()
                        .event("deleterequeststates")
                        .id("none")
                        .data(this.requestStates(true))
                        .build(),
                    ServerSentEvent
                        .builder<Any>()
                        .event("deleterequestslastmonth")
                        .id("none")
                        .data(this.requestsLastMonth(true))
                        .build(),
                    ServerSentEvent
                        .builder<Any>()
                        .event("deleterequestpatientstates")
                        .id("none")
                        .data(this.requestPatientStates(true))
                        .build(),
                    ServerSentEvent
                        .builder<Any>()
                        .event("newrequest")
                        .id("none")
                        .data("newrequest")
                        .build(),
                ),
            )
        }
}

data class NameValue(
    val name: String,
    val value: Int,
    val color: String,
)

data class DateNameValues(
    val date: String,
    val nameValues: NameValues,
)

@JsonPropertyOrder(value = ["error", "warning", "success", "no_consent", "duplication", "blocked_initial", "unknown"])
data class NameValues(
    val error: Int = 0,
    val warning: Int = 0,
    val success: Int = 0,
    @field:JsonProperty("no_consent")
    val noConsent: Int = 0,
    val duplication: Int = 0,
    @field:JsonProperty("blocked_initial")
    val blockedInitial: Int = 0,
    val unknown: Int = 0,
)
