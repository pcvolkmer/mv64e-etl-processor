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

package dev.dnpm.etl.processor.web

import de.ukw.ccc.bwhc.dto.MtbFile
import dev.dnpm.etl.processor.output.MtbFileSender
import dev.dnpm.etl.processor.output.RestMtbFileSender
import dev.dnpm.etl.processor.pseudonym.PseudonymizeService
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class MtbFileController(
    private val pseudonymizeService: PseudonymizeService,
    private val senders: List<MtbFileSender>
) {

    private val logger = LoggerFactory.getLogger(MtbFileController::class.java)

    @PostMapping(path = ["/mtbfile"])
    fun mtbFile(@RequestBody mtbFile: MtbFile) {
        val pseudonymized = pseudonymizeService.pseudonymize(mtbFile)
        senders.forEach {
            val success = it.send(pseudonymized)
            if (success) {
                logger.info("Sent file for Patient '{}' using '{}'", pseudonymized.patient.id, it.javaClass.simpleName)
            } else {
                logger.error("Error sending file for Patient '{}' using '{}'", pseudonymized.patient.id, it.javaClass.simpleName)
            }
        }
    }

}