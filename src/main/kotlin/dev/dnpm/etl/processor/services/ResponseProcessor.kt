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
import dev.dnpm.etl.processor.monitoring.Report
import dev.dnpm.etl.processor.monitoring.RequestRepository
import dev.dnpm.etl.processor.monitoring.RequestStatus
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import reactor.core.publisher.Sinks
import java.time.Instant
import java.util.*

@Service
class ResponseProcessor(
    private val requestRepository: RequestRepository,
    private val statisticsUpdateProducer: Sinks.Many<Any>,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(ResponseProcessor::class.java)

    @EventListener(classes = [ResponseEvent::class])
    fun handleResponseEvent(event: ResponseEvent) {
        requestRepository.findByUuidEquals(event.requestUuid).ifPresentOrElse({
            it.processedAt = event.timestamp
            it.status = event.status

            when (event.status) {
                RequestStatus.SUCCESS -> {
                    it.report = Report(
                        "Keine Probleme erkannt",
                    )
                }

                RequestStatus.WARNING -> {
                    it.report = Report(
                        "Warnungen über mangelhafte Daten",
                        objectMapper.writeValueAsString(event.body)
                    )
                }

                RequestStatus.ERROR -> {
                    it.report = Report(
                        "Fehler bei der Datenübertragung oder Inhalt nicht verarbeitbar",
                        objectMapper.writeValueAsString(event.body)
                    )
                }

                RequestStatus.DUPLICATION -> {
                    it.report = Report(
                        "Duplikat erkannt"
                    )
                }

                else -> {
                    logger.error("Cannot process response: Unknown response code!")
                    return@ifPresentOrElse
                }
            }

            requestRepository.save(it)

            statisticsUpdateProducer.emitNext("", Sinks.EmitFailureHandler.FAIL_FAST)
        }, {
            logger.error("Response for unknown request '${event.requestUuid}'!")
        })
    }

}

data class ResponseEvent(
    val requestUuid: String,
    val timestamp: Instant,
    val status: RequestStatus,
    val body: Optional<String> = Optional.empty()
)