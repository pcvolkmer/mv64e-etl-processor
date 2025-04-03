/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2025  Comprehensive Cancer Center Mainfranken, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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

package dev.dnpm.etl.processor.output

import dev.dnpm.etl.processor.PatientPseudonym
import dev.dnpm.etl.processor.config.RestTargetProperties
import dev.dnpm.etl.processor.monitoring.ReportService
import org.springframework.retry.support.RetryTemplate
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

class RestBwhcMtbFileSender(
    restTemplate: RestTemplate,
    private val restTargetProperties: RestTargetProperties,
    retryTemplate: RetryTemplate,
    reportService: ReportService,
) : RestMtbFileSender(restTemplate, restTargetProperties, retryTemplate, reportService) {

    override fun sendUrl(): String {
        return UriComponentsBuilder
            .fromUriString(restTargetProperties.uri.toString())
            .pathSegment("MTBFile")
            .toUriString()
    }

    override fun deleteUrl(patientId: PatientPseudonym): String {
        return UriComponentsBuilder
            .fromUriString(restTargetProperties.uri.toString())
            .pathSegment("Patient")
            .pathSegment(patientId.value)
            .toUriString()
    }

}