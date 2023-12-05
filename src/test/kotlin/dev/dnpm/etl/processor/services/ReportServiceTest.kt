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

package dev.dnpm.etl.processor.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import dev.dnpm.etl.processor.monitoring.ReportService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ReportServiceTest {

    private lateinit var reportService: ReportService

    @BeforeEach
    fun setup() {
        this.reportService = ReportService(ObjectMapper().registerModule(KotlinModule.Builder().build()))
    }

    @Test
    fun shouldParseDataQualityReport() {
        val json = """
            {
                "patient": "4711",
                "issues": [
                    { "severity": "info", "message": "Info Message" },
                    { "severity": "warning", "message": "Warning Message" },
                    { "severity": "error", "message": "Error Message" }
                ]
            }
        """.trimIndent()

        val actual = this.reportService.deserialize(json)

        assertThat(actual).hasSize(3)
        assertThat(actual[0].severity).isEqualTo(ReportService.Severity.INFO)
        assertThat(actual[0].message).isEqualTo("Info Message")
        assertThat(actual[1].severity).isEqualTo(ReportService.Severity.WARNING)
        assertThat(actual[1].message).isEqualTo("Warning Message")
        assertThat(actual[1].severity).isEqualTo(ReportService.Severity.ERROR)
        assertThat(actual[1].message).isEqualTo("Error Message")
    }

    @Test
    fun shouldReturnSyntheticDataQualityReportOnParserError() {
        val invalidResponse = "Invalid Response Data"

        val actual = this.reportService.deserialize(invalidResponse)

        assertThat(actual).hasSize(1)
        assertThat(actual[0].severity).isEqualTo(ReportService.Severity.ERROR)
        assertThat(actual[0].message).isEqualTo("Not parsable data quality report '$invalidResponse'")
    }

}