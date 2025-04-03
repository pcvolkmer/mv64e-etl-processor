/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2024  Comprehensive Cancer Center Mainfranken, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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

import dev.dnpm.etl.processor.config.RestTargetProperties
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.dnpm.etl.processor.PatientPseudonym
import dev.dnpm.etl.processor.monitoring.ReportService
import dev.dnpm.etl.processor.monitoring.asRequestStatus
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.retry.support.RetryTemplate
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestTemplate

abstract class RestMtbFileSender(
    private val restTemplate: RestTemplate,
    private val restTargetProperties: RestTargetProperties,
    private val retryTemplate: RetryTemplate,
    private val reportService: ReportService
) : MtbFileSender {

    private val logger = LoggerFactory.getLogger(RestMtbFileSender::class.java)

    abstract fun sendUrl(): String

    abstract fun deleteUrl(patientId: PatientPseudonym): String

    override fun send(request: MtbFileSender.MtbFileRequest): MtbFileSender.Response {
        try {
            return retryTemplate.execute<MtbFileSender.Response, Exception> {
                val headers = getHttpHeaders()
                val entityReq = HttpEntity(request.mtbFile, headers)
                val response = restTemplate.postForEntity(
                    sendUrl(),
                    entityReq,
                    String::class.java
                )
                if (!response.statusCode.is2xxSuccessful) {
                    logger.warn("Error sending to remote system: {}", response.body)
                    return@execute MtbFileSender.Response(
                        reportService.deserialize(response.body).asRequestStatus(),
                        "Status-Code: ${response.statusCode.value()}"
                    )
                }
                logger.debug("Sent file via RestMtbFileSender")
                return@execute MtbFileSender.Response(reportService.deserialize(response.body).asRequestStatus(), response.body.orEmpty())
            }
        } catch (e: IllegalArgumentException) {
            logger.error("Not a valid URI to export to: '{}'", restTargetProperties.uri!!)
        } catch (e: RestClientResponseException) {
            logger.info(restTargetProperties.uri!!.toString())
            logger.error("Request data not accepted by remote system", e)
            return MtbFileSender.Response(reportService.deserialize(e.responseBodyAsString).asRequestStatus(), e.responseBodyAsString)
        }
        return MtbFileSender.Response(RequestStatus.ERROR, "Sonstiger Fehler bei der Übertragung")
    }

    override fun send(request: MtbFileSender.DeleteRequest): MtbFileSender.Response {
        try {
            return retryTemplate.execute<MtbFileSender.Response, Exception> {
                val headers = getHttpHeaders()
                val entityReq = HttpEntity(null, headers)
                restTemplate.delete(
                    deleteUrl(request.patientId),
                    entityReq,
                    String::class.java
                )
                logger.debug("Sent file via RestMtbFileSender")
                return@execute MtbFileSender.Response(RequestStatus.SUCCESS)
            }
        } catch (e: IllegalArgumentException) {
            logger.error("Not a valid URI to export to: '{}'", restTargetProperties.uri!!)
        } catch (e: RestClientException) {
            logger.info(restTargetProperties.uri!!.toString())
            logger.error("Cannot send data to remote system", e)
        }
        return MtbFileSender.Response(RequestStatus.ERROR, "Sonstiger Fehler bei der Übertragung")
    }

    override fun endpoint(): String {
        return this.restTargetProperties.uri.orEmpty()
    }

    private fun getHttpHeaders(): HttpHeaders {
        val username = restTargetProperties.username
        val password = restTargetProperties.password
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        if (username.isNullOrBlank() || password.isNullOrBlank()) {
            return headers
        }

        headers.setBasicAuth(username, password)
        return headers
    }

}