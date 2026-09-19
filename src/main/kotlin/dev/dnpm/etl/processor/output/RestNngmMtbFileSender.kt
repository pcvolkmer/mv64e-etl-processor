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

import dev.dnpm.etl.processor.config.SwitchProperties
import dev.dnpm.etl.processor.monitoring.ReportService
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.dnpm.etl.processor.monitoring.asRequestStatus
import dev.pcvolkmer.mv64e.model.MtbDiagnosis
import dev.pcvolkmer.mv64e.model.PatientRecord
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.retry.support.RetryTemplate
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange
import org.springframework.web.util.UriComponentsBuilder

class RestNngmMtbFileSender(
    private val restTemplate: RestTemplate,
    private val switchProperties: SwitchProperties,
    private val retryTemplate: RetryTemplate,
    private val reportService: ReportService,
) : SwitchedMtbFileSender {
    private val logger = LoggerFactory.getLogger(RestNngmMtbFileSender::class.java)

    fun sendUrl(): String =
        UriComponentsBuilder
            .fromUriString(switchProperties.nngm?.uri.toString())
            .toUriString()

    override fun supportsDiagnosis(diagnosis: MtbDiagnosis): Boolean {
        val code = diagnosis.code?.code ?: return false

        if (code.isBlank()) {
            return false
        }

        this.switchProperties.nngm?.icd10?.forEach {
            if (code.startsWith(it)) {
                return true
            }
        }
        return false
    }

    override fun send(request: MtbFileRequest<PatientRecord>): MtbFileSender.Response {
        try {
            return retryTemplate.execute<MtbFileSender.Response, Exception> {
                val entityReq = HttpEntity(request.content, getHttpHeaders())
                val response =
                    restTemplate.exchange<String>(sendUrl(), HttpMethod.POST, entityReq)
                if (!response.statusCode.is2xxSuccessful) {
                    logger.warn("Error sending to remote system: {}", response.body)
                    return@execute MtbFileSender.Response(
                        reportService.deserialize(response.body).asRequestStatus(),
                        "Status-Code: ${response.statusCode.value()}",
                    )
                }
                logger.debug("Sent file via RestNngmMtbFileSender")
                return@execute MtbFileSender.Response(
                    reportService.deserialize(response.body).asRequestStatus(),
                    response.body.orEmpty(),
                )
            }
        } catch (_: IllegalArgumentException) {
            logger.error("Not a valid URI to export to: '{}'", switchProperties.nngm?.uri!!)
        } catch (e: RestClientResponseException) {
            logger.info(switchProperties.nngm?.uri!!)
            logger.error("Request data not accepted by remote system", e)
            return MtbFileSender.Response(
                reportService.deserialize(e.responseBodyAsString).asRequestStatus(),
                e.responseBodyAsString,
            )
        }
        return MtbFileSender.Response(RequestStatus.ERROR, "Sonstiger Fehler bei der Übertragung")
    }

    fun endpoint(): String =
        this.switchProperties.nngm
            ?.uri
            .orEmpty()

    private fun getHttpHeaders(): HttpHeaders {
        val apiKey = switchProperties.nngm?.apiKey
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        if (apiKey.isNullOrBlank()) {
            return headers
        }

        headers.set(HttpHeaders.AUTHORIZATION, "X-API-KEY $apiKey")
        return headers
    }
}
