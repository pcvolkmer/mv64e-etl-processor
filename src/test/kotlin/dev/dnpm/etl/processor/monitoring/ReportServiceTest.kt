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

import dev.dnpm.etl.processor.config.JacksonConfig
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ReportServiceTest {

  lateinit var service: ReportService

  @BeforeEach
  fun setUp() {
    val jackson3Config = JacksonConfig()
    service = ReportService(jackson3Config.jsonMapper())
  }

  @Test
  fun shouldParseDataQualityReport() {
    val dataQualityReport =
        Objects.requireNonNull(this.javaClass.classLoader.getResource("dip-response.json"))
            .readText()

    val actual = service.deserialize(dataQualityReport)

    assertThat(actual).isNotNull
    assertThat(actual).hasSize(6)
    assertThat(actual[0].severity).isEqualTo(ReportService.Severity.FATAL)
    assertThat(actual[1].severity).isEqualTo(ReportService.Severity.ERROR)
    assertThat(actual[2].severity).isEqualTo(ReportService.Severity.WARNING)
    assertThat(actual[3].severity).isEqualTo(ReportService.Severity.WARNING)
    assertThat(actual[4].severity).isEqualTo(ReportService.Severity.WARNING)
    assertThat(actual[5].severity).isEqualTo(ReportService.Severity.INFO)
  }
}
