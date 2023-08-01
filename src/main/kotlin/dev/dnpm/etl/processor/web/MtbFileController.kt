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
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.dnpm.etl.processor.services.RequestProcessor
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
class MtbFileController(
    private val requestProcessor: RequestProcessor,
) {

    private val logger = LoggerFactory.getLogger(MtbFileController::class.java)

    @PostMapping(path = ["/mtbfile"])
    fun mtbFile(@RequestBody mtbFile: MtbFile): ResponseEntity<Void> {
        val requestStatus = requestProcessor.processMtbFile(mtbFile)

        return if (requestStatus == RequestStatus.ERROR) {
            ResponseEntity.unprocessableEntity().build()
        } else {
            ResponseEntity.noContent().build()
        }
    }

    @DeleteMapping(path = ["/mtbfile/{patientId}"])
    fun deleteData(@PathVariable patientId: String): ResponseEntity<Void> {
        val requestStatus = requestProcessor.processDeletion(patientId)

        return if (requestStatus == RequestStatus.ERROR) {
            ResponseEntity.unprocessableEntity().build()
        } else {
            ResponseEntity.noContent().build()
        }
    }

}