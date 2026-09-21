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

abstract class RestMtbFileSender(
    private val restTemplate: RestTemplate,
    private val restTargetProperties: RestTargetProperties,
    private val retryTemplate: RetryTemplate,
    private val reportService: ReportService,
) : MtbFileSender {
    private val logger = LoggerFactory.getLogger(RestMtbFileSender::class.java)

    abstract fun sendUrl(): String

    abstract fun deleteUrl(patientId: PatientPseudonym): String

    override fun <T> send(request: MtbFileRequest<T>): MtbFileSender.Response {
        try {
            return retryTemplate.execute<MtbFileSender.Response, Exception> {
                val headers = getHttpHeaders(request)
                val entityReq = HttpEntity(request.content, headers)
                val response =
                    restTemplate.exchange<String>(sendUrl(), HttpMethod.POST, entityReq)
                if (!response.statusCode.is2xxSuccessful) {
                    logger.warn("Error sending to remote system: {}", response.body)
                    return@execute MtbFileSender.Response(
                        reportService.deserialize(response.body).asRequestStatus(),
                        "Status-Code: ${response.statusCode.value()}",
                    )
                }
                logger.debug("Sent file via ${this::class.java.simpleName}")
                return@execute MtbFileSender.Response(
                    reportService.deserialize(response.body).asRequestStatus(),
                    response.body.orEmpty(),
                )
            }
        } catch (_: IllegalArgumentException) {
            logger.error("Not a valid URI to export to: '{}'", restTargetProperties.uri!!)
        } catch (e: RestClientResponseException) {
            logger.info(restTargetProperties.uri!!.toString())
            logger.error("Request data not accepted by remote system", e)
            return MtbFileSender.Response(
                RequestStatus.ERROR,
                e.responseBodyAsString,
            )
        }
        return MtbFileSender.Response(RequestStatus.ERROR, "Sonstiger Fehler bei der Übertragung")
    }

    override fun send(request: DeleteRequest): MtbFileSender.Response {
        try {
            return retryTemplate.execute<MtbFileSender.Response, Exception> {
                val headers = getHttpHeaders(request)
                val entityReq = HttpEntity(null, headers)
                restTemplate.delete(deleteUrl(request.patientId), entityReq, String::class.java)
                logger.debug("Sent file via RestDipMtbFileSender")
                return@execute MtbFileSender.Response(RequestStatus.SUCCESS)
            }
        } catch (_: IllegalArgumentException) {
            logger.error("Not a valid URI to export to: '{}'", restTargetProperties.uri!!)
        } catch (e: RestClientException) {
            logger.info(restTargetProperties.uri!!.toString())
            logger.error("Cannot send data to remote system", e)
        }
        return MtbFileSender.Response(RequestStatus.ERROR, "Sonstiger Fehler bei der Übertragung")
    }

    override fun endpoint(): String = this.restTargetProperties.uri.orEmpty()

    protected abstract fun getHttpHeaders(request: MtbRequest): HttpHeaders
}
