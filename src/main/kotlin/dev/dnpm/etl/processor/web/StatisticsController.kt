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

import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.dnpm.etl.processor.monitoring.RequestType
import dev.dnpm.etl.processor.monitoring.SubmissionType
import dev.dnpm.etl.processor.services.RequestService
import net.sf.saxon.tree.tiny.Statistics
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Controller
@RequestMapping(path = ["/statistics"])
class StatisticsController(
    private val requestService: RequestService,
) {
    @GetMapping
    fun index(model: Model): String {
        val submissions =
            requestService
                .findAll()
                .asSequence()
                .filter { it.type == RequestType.MTB_FILE }
                .filter { listOf(RequestStatus.SUCCESS, RequestStatus.WARNING).contains(it.status) }
                .sortedByDescending { it.processedAt }
                .groupBy {
                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneId.systemDefault())
                    formatter.format(it.processedAt)
                }.map {
                    mapOf(
                        "month" to it.key,
                        "accepted" to it.value.count { it.submissionAccepted },
                        "initial" to it.value.count { it.submissionType == SubmissionType.INITIAL },
                        "submissions" to it.value.size,
                    )
                }.toList()

        model.addAttribute("now", Instant.now())
        model.addAttribute("submissions", submissions)
        return "statistics"
    }
}
