/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2023       Comprehensive Cancer Center Mainfranken
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

package dev.dnpm.etl.processor.monitoring

import dev.dnpm.etl.processor.monitoring.ReportService.Issue
import dev.dnpm.etl.processor.monitoring.ReportService.Severity
import tools.jackson.core.JacksonException
import tools.jackson.databind.EnumNamingStrategies
import tools.jackson.databind.annotation.EnumNaming
import tools.jackson.databind.json.JsonMapper
import java.util.*

class ReportService(private val jsonMapper: JsonMapper) {

    fun deserialize(dataQualityReport: String?): List<Issue> {
        if (dataQualityReport.isNullOrBlank()) {
            return listOf()
        }
        return try {
            jsonMapper.readValue(dataQualityReport, DataQualityReport::class.java).issues.sortedBy {
                it.severity
            }
        } catch (_: JacksonException) {
            val otherIssue =
                Issue(Severity.ERROR, Optional.of("Not parsable data quality report '$dataQualityReport'"))
            return listOf(otherIssue)
        } catch (e: Exception) {
            throw e
        }
    }

    private data class DataQualityReport(
        val issues: List<Issue>
    )

    data class Issue(
        val severity: Severity,
        val message: Optional<String> = Optional.empty(),
        val details: Optional<String> = Optional.empty(),
        val path: Optional<String> = Optional.empty(),
    ) {
        fun getMessage() = message.orElse(details.orElse("No details available"))
    }

    @EnumNaming(EnumNamingStrategies.LowerCaseStrategy::class)
    enum class Severity(val value: String) {
        FATAL("fatal"),
        ERROR("error"),
        WARNING("warning"),
        INFO("info"),
    }
}

fun List<Issue>.asRequestStatus(): RequestStatus {
    val severity = this.minOfOrNull { it.severity }
    return when (severity) {
        Severity.FATAL,
        Severity.ERROR -> RequestStatus.ERROR
        Severity.WARNING -> RequestStatus.WARNING
        else -> RequestStatus.SUCCESS
    }
}
