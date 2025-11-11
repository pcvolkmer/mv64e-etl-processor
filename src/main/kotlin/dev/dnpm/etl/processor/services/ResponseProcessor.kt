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

import dev.dnpm.etl.processor.RequestId
import dev.dnpm.etl.processor.monitoring.Report
import dev.dnpm.etl.processor.monitoring.RequestStatus
import java.time.Instant
import java.util.*
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import reactor.core.publisher.Sinks

@Service
class ResponseProcessor(
    private val requestService: RequestService,
    private val statisticsUpdateProducer: Sinks.Many<Any>,
) {

  private val logger = LoggerFactory.getLogger(ResponseProcessor::class.java)

  @EventListener(classes = [ResponseEvent::class])
  fun handleResponseEvent(event: ResponseEvent) {
    requestService
        .findByUuid(event.requestUuid)
        .ifPresentOrElse(
            {
              it.processedAt = event.timestamp
              it.status = event.status

              when (event.status) {
                RequestStatus.SUCCESS -> {
                  it.report =
                      Report(
                          "Keine Probleme erkannt",
                      )
                }

                RequestStatus.WARNING -> {
                  it.report = Report("Warnungen über mangelhafte Daten", event.body.orElse(""))
                }

                RequestStatus.ERROR -> {
                  it.report =
                      Report(
                          "Fehler bei der Datenübertragung oder Inhalt nicht verarbeitbar",
                          event.body.orElse(""),
                      )
                }

                RequestStatus.DUPLICATION -> {
                  it.report = Report("Duplikat erkannt")
                }

                RequestStatus.NO_CONSENT -> {
                  it.report = Report("Einwilligung Status fehlt, widerrufen oder ungeklärt.")
                }

                else -> {
                  logger.error("Cannot process response: Unknown response!")
                  return@ifPresentOrElse
                }
              }

              requestService.save(it)

              statisticsUpdateProducer.emitNext("", Sinks.EmitFailureHandler.FAIL_FAST)
            },
            { logger.error("Response for unknown request '${event.requestUuid}'!") },
        )
  }
}

data class ResponseEvent(
    val requestUuid: RequestId,
    val timestamp: Instant,
    val status: RequestStatus,
    val body: Optional<String> = Optional.empty(),
)
