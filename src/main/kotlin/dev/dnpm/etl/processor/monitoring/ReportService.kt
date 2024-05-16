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

package dev.dnpm.etl.processor.monitoring

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper

class ReportService(
    private val objectMapper: ObjectMapper
) {

    fun deserialize(dataQualityReport: String?): List<Issue> {
        if (dataQualityReport.isNullOrBlank()) {
            return listOf()
        }
        return try {
            objectMapper
                .readValue(dataQualityReport, DataQualityReport::class.java)
                .issues
                .sortedBy { it.severity }
        } catch (e: Exception) {
            val otherIssue =
                Issue(Severity.ERROR, "Not parsable data quality report '$dataQualityReport'")
            return when (e) {
                is JsonMappingException -> listOf(otherIssue)
                is JsonParseException -> listOf(otherIssue)
                else -> throw e
            }
        }
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class DataQualityReport(val issues: List<Issue>)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Issue(val severity: Severity, @JsonAlias("details") val message: String)

    enum class Severity(@JsonValue val value: String) {
        FATAL("fatal"),
        ERROR("error"),
        WARNING("warning"),
        INFO("info")
    }
}