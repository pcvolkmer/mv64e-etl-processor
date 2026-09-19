/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2023       Comprehensive Cancer Center Mainfranken
 * Copyright (c) 2025-2026  Paul-Christian Volkmer, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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

import dev.dnpm.etl.processor.CustomMediaType
import dev.dnpm.etl.processor.PatientPseudonym
import dev.dnpm.etl.processor.config.RestTargetProperties
import dev.dnpm.etl.processor.monitoring.ReportService
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.dnpm.etl.processor.monitoring.asRequestStatus
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.retry.support.RetryTemplate
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange
import org.springframework.web.util.UriComponentsBuilder

class RestDipMtbFileSender(
    restTemplate: RestTemplate,
    private val restTargetProperties: RestTargetProperties,
    retryTemplate: RetryTemplate,
    reportService: ReportService,
) : RestMtbFileSender(restTemplate, restTargetProperties, retryTemplate, reportService) {
    override fun sendUrl(): String =
        UriComponentsBuilder
            .fromUriString(restTargetProperties.uri.toString())
            .pathSegment("mtb")
            .pathSegment("etl")
            .pathSegment("patient-record")
            .toUriString()

    override fun deleteUrl(patientId: PatientPseudonym): String =
        UriComponentsBuilder
            .fromUriString(restTargetProperties.uri.toString())
            .pathSegment("mtb")
            .pathSegment("etl")
            .pathSegment("patient")
            .pathSegment(patientId.value)
            .toUriString()

    override fun endpoint(): String = this.restTargetProperties.uri.orEmpty()

    override fun getHttpHeaders(request: MtbRequest): HttpHeaders {
        val username = restTargetProperties.username
        val password = restTargetProperties.password
        val headers = HttpHeaders()
        headers.contentType =
            when (request) {
                is DnpmV2MtbFileRequest -> CustomMediaType.APPLICATION_VND_DNPM_V2_MTB_JSON
                else -> MediaType.APPLICATION_JSON
            }

        if (username.isNullOrBlank() || password.isNullOrBlank()) {
            return headers
        }

        headers.setBasicAuth(username, password)
        return headers
    }
}
