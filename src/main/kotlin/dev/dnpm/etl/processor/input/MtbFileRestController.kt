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

package dev.dnpm.etl.processor.input

import de.ukw.ccc.bwhc.dto.Consent
import de.ukw.ccc.bwhc.dto.MtbFile
import dev.dnpm.etl.processor.CustomMediaType
import dev.dnpm.etl.processor.PatientId
import dev.dnpm.etl.processor.services.RequestProcessor
import dev.pcvolkmer.mv64e.mtb.Mtb
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping(path = ["mtbfile", "mtb"])
class MtbFileRestController(
    private val requestProcessor: RequestProcessor,
) {

    private val logger = LoggerFactory.getLogger(MtbFileRestController::class.java)

    @GetMapping
    fun info(): ResponseEntity<String> {
        return ResponseEntity.ok("Test")
    }

    @PostMapping( consumes = [ MediaType.APPLICATION_JSON_VALUE ] )
    fun mtbFile(@RequestBody mtbFile: MtbFile): ResponseEntity<Unit> {
        if (mtbFile.consent.status == Consent.Status.ACTIVE) {
            logger.debug("Accepted MTB File for processing")
            requestProcessor.processMtbFile(mtbFile)
        } else {
            logger.debug("Accepted MTB File and process deletion")
            val patientId = PatientId(mtbFile.patient.id)
            requestProcessor.processDeletion(patientId)
        }
        return ResponseEntity.accepted().build()
    }

    @PostMapping( consumes = [ CustomMediaType.APPLICATION_VND_DNPM_V2_MTB_JSON_VALUE] )
    fun mtbFile(@RequestBody mtbFile: Mtb): ResponseEntity<Unit> {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
    }

    @DeleteMapping(path = ["{patientId}"])
    fun deleteData(@PathVariable patientId: String): ResponseEntity<Unit> {
        logger.debug("Accepted patient ID to process deletion")
        requestProcessor.processDeletion(PatientId(patientId))
        return ResponseEntity.accepted().build()
    }

}
