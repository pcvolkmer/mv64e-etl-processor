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

import dev.dnpm.etl.processor.NotFoundException
import dev.dnpm.etl.processor.PatientPseudonym
import dev.dnpm.etl.processor.RequestId
import dev.dnpm.etl.processor.monitoring.ReportService
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.dnpm.etl.processor.services.RequestService
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping(path = ["/"])
class HomeController(
    private val requestService: RequestService,
    private val reportService: ReportService,
) {
    @GetMapping
    fun index(
        @PageableDefault(page = 0, size = 20, sort = ["processedAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
        model: Model,
    ): String {
        val requests = requestService.findAll(pageable)
        model.addAttribute("requests", requests)

        return "index"
    }

    @GetMapping(path = ["patient/{patientPseudonym}"])
    fun byPatient(
        @PathVariable patientPseudonym: PatientPseudonym,
        @PageableDefault(page = 0, size = 20, sort = ["processedAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
        model: Model,
    ): String {
        val requests = requestService.findRequestByPatientId(patientPseudonym, pageable)
        model.addAttribute("patientPseudonym", patientPseudonym.value)
        model.addAttribute("requests", requests)

        return "index"
    }

    @GetMapping(path = ["/report/{id}"])
    fun report(
        @PathVariable id: RequestId,
        model: Model,
    ): String {
        val request = requestService.findByUuid(id).orElse(null) ?: throw NotFoundException()
        model.addAttribute("request", request)
        model.addAttribute("issues", reportService.deserialize(request.report?.dataQualityReport))

        return "report"
    }

    @PutMapping(path = ["/submission/{id}/accepted"])
    fun acceptReport(
        @PathVariable id: RequestId,
        model: Model,
    ): String {
        val request = requestService.findByUuid(id).orElse(null) ?: throw NotFoundException()
        request.submissionAccepted = true
        val savedRequest = requestService.save(request)

        model.addAttribute("request", savedRequest)

        return "fragments :: accept-initial"
    }

    @DeleteMapping(path = ["/submission/{id}/accepted"])
    fun unacceptReport(
        @PathVariable id: RequestId,
        model: Model,
    ): String {
        val request = requestService.findByUuid(id).orElse(null) ?: throw NotFoundException()
        request.submissionAccepted = false
        val savedRequest = requestService.save(request)

        model.addAttribute("request", savedRequest)

        return "fragments :: accept-initial"
    }
}
