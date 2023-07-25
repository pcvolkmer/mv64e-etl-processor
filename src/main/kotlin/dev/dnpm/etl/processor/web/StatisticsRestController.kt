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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit

@RestController
@RequestMapping(path = ["/statistics"])
class StatisticsRestController(
    private val requestRepository: RequestRepository
) {

    @GetMapping(path = ["requeststates"])
    fun requestStates(): List<NameValue> {
        return requestRepository.findAll()
            .groupBy { it.status }
            .map {
                val color = when (it.key) {
                    RequestStatus.ERROR -> "red"
                    RequestStatus.WARNING -> "darkorange"
                    RequestStatus.SUCCESS -> "green"
                    else -> "slategray"
                }
                NameValue(it.key.toString(), it.value.size, color)
            }
            .sortedByDescending { it.value }
    }

    @GetMapping(path = ["requestslastmonth"])
    fun requestsLastMonth(): List<DateNameValues> {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("Europe/Berlin"))
        val data = requestRepository.findAll()
            .filter { it.processedAt.isAfter(Instant.now().minus(30, ChronoUnit.DAYS)) }
            .groupBy { formatter.format(it.processedAt) }
            .map {
                val requestList = it.value
                    .groupBy { it.status }
                    .map {
                        Pair(it.key, it.value.size)
                    }
                    .toMap()
                Pair(
                    it.key.toString(),
                    DateNameValues(it.key.toString(), NameValues(
                        error = requestList[RequestStatus.ERROR] ?: 0,
                        warning = requestList[RequestStatus.WARNING] ?: 0,
                        success = requestList[RequestStatus.SUCCESS] ?: 0,
                        duplication = requestList[RequestStatus.DUPLICATION] ?: 0,
                        unknown = requestList[RequestStatus.UNKNOWN] ?: 0,
                    ))
                )
            }.toMap()

        return (0L..30L).map { Instant.now().minus(it, ChronoUnit.DAYS) }
            .map { formatter.format(it) }
            .map {
                DateNameValues(it, data[it]?.nameValues ?: NameValues())
            }
            .sortedBy { it.date }
    }

}

data class NameValue(val name: String, val value: Int, val color: String)

data class DateNameValues(val date: String, val nameValues: NameValues)

data class NameValues(val error: Int = 0, val warning: Int = 0, val success: Int = 0, val duplication: Int = 0, val unknown: Int = 0)