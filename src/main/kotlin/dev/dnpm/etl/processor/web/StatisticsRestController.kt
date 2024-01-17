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

package dev.dnpm.etl.processor.web

import dev.dnpm.etl.processor.monitoring.RequestRepository
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.dnpm.etl.processor.monitoring.RequestType
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
    @Qualifier("statisticsUpdateProducer")
    private val statisticsUpdateProducer: Sinks.Many<Any>,
    private val requestRepository: RequestRepository
) {

    @GetMapping(path = ["requeststates"])
    fun requestStates(@RequestParam(required = false, defaultValue = "false") delete: Boolean): List<NameValue> {
        val states = if (delete) {
            requestRepository.countDeleteStates()
        } else {
            requestRepository.countStates()
        }

        return states
            .map {
                val color = when (it.status) {
                    RequestStatus.ERROR -> "red"
                    RequestStatus.WARNING -> "darkorange"
                    RequestStatus.SUCCESS -> "green"
                    else -> "slategray"
                }
                NameValue(it.status.toString(), it.count, color)
            }
            .sortedByDescending { it.value }
    }

    @GetMapping(path = ["requestslastmonth"])
    fun requestsLastMonth(
        @RequestParam(
            required = false,
            defaultValue = "false"
        ) delete: Boolean
    ): List<DateNameValues> {
        val requestType = if (delete) {
            RequestType.DELETE
        } else {
            RequestType.MTB_FILE
        }

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("Europe/Berlin"))
        val data = requestRepository.findAll()
            .filter { it.type == requestType }
            .filter { it.processedAt.isAfter(Instant.now().minus(30, ChronoUnit.DAYS)) }
            .groupBy { formatter.format(it.processedAt) }
            .map {
                val requestList = it.value
                    .groupBy { request -> request.status }
                    .map { request ->
                        Pair(request.key, request.value.size)
                    }
                    .toMap()
                Pair(
                    it.key.toString(),
                    DateNameValues(
                        it.key.toString(), NameValues(
                            error = requestList[RequestStatus.ERROR] ?: 0,
                            warning = requestList[RequestStatus.WARNING] ?: 0,
                            success = requestList[RequestStatus.SUCCESS] ?: 0,
                            duplication = requestList[RequestStatus.DUPLICATION] ?: 0,
                            unknown = requestList[RequestStatus.UNKNOWN] ?: 0,
                        )
                    )
                )
            }.toMap()

        return (0L..30L).map { Instant.now().minus(it, ChronoUnit.DAYS) }
            .map { formatter.format(it) }
            .map {
                DateNameValues(it, data[it]?.nameValues ?: NameValues())
            }
            .sortedBy { it.date }
    }

    @GetMapping(path = ["requestpatientstates"])
    fun requestPatientStates(@RequestParam(required = false, defaultValue = "false") delete: Boolean): List<NameValue> {
        val states = if (delete) {
            requestRepository.findPatientUniqueDeleteStates()
        } else {
            requestRepository.findPatientUniqueStates()
        }

        return states.map {
            val color = when (it.status) {
                RequestStatus.ERROR -> "red"
                RequestStatus.WARNING -> "darkorange"
                RequestStatus.SUCCESS -> "green"
                else -> "slategray"
            }
            NameValue(it.status.toString(), it.count, color)
        }
    }

    @GetMapping(path = ["events"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun updater(): Flux<ServerSentEvent<Any>> {
        return statisticsUpdateProducer.asFlux().flatMap {
            println(it)
            Flux.fromIterable(
                listOf(
                    ServerSentEvent.builder<Any>()
                        .event("requeststates").id("none").data(this.requestStates(false))
                        .build(),
                    ServerSentEvent.builder<Any>()
                        .event("requestslastmonth").id("none").data(this.requestsLastMonth(false))
                        .build(),
                    ServerSentEvent.builder<Any>()
                        .event("requestpatientstates").id("none").data(this.requestPatientStates(false))
                        .build(),

                    ServerSentEvent.builder<Any>()
                        .event("deleterequeststates").id("none").data(this.requestStates(true))
                        .build(),
                    ServerSentEvent.builder<Any>()
                        .event("deleterequestslastmonth").id("none").data(this.requestsLastMonth(true))
                        .build(),
                    ServerSentEvent.builder<Any>()
                        .event("deleterequestpatientstates").id("none").data(this.requestPatientStates(true))
                        .build(),

                    ServerSentEvent.builder<Any>()
                        .event("newrequest").id("none").data("newrequest")
                        .build()
                )
            )

        }
    }

}

data class NameValue(val name: String, val value: Int, val color: String)

data class DateNameValues(val date: String, val nameValues: NameValues)

data class NameValues(
    val error: Int = 0,
    val warning: Int = 0,
    val success: Int = 0,
    val duplication: Int = 0,
    val unknown: Int = 0
)