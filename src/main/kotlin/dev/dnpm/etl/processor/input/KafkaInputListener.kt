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

package dev.dnpm.etl.processor.input

import de.ukw.ccc.bwhc.dto.Consent
import de.ukw.ccc.bwhc.dto.MtbFile
import dev.dnpm.etl.processor.services.RequestProcessor
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.listener.MessageListener

class KafkaInputListener(
    private val requestProcessor: RequestProcessor
) : MessageListener<String, MtbFile> {
    private val logger = LoggerFactory.getLogger(KafkaInputListener::class.java)

    override fun onMessage(data: ConsumerRecord<String, MtbFile>) {
        if (data.value().consent.status == Consent.Status.ACTIVE) {
            logger.debug("Accepted MTB File for processing")
            requestProcessor.processMtbFile(data.value())
        } else {
            logger.debug("Accepted MTB File and process deletion")
            requestProcessor.processDeletion(data.value().patient.id)
        }
    }
}