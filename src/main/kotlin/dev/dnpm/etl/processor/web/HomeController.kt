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
import dev.dnpm.etl.processor.Tan
import dev.dnpm.etl.processor.config.AppConfigProperties
import dev.dnpm.etl.processor.monitoring.ReportService
import dev.dnpm.etl.processor.services.RequestService
import dev.dnpm.etl.processor.services.filter
import org.springframework.core.convert.converter.Converter
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*

@Controller
@RequestMapping(path = ["/"])
class HomeController(
    private val requestService: RequestService,
    private val reportService: ReportService,
    private val appConfigProperties: AppConfigProperties,
) {
    @GetMapping
    fun index(
        @RequestParam(name = "q", required = false) queryString: String?,
        @RequestParam(name = "f", required = false) filter: RequestService.Filter?,
        @PageableDefault(page = 0, size = 10, sort = ["processedAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
        model: Model,
    ): String {
        val isAdminOrUser =
            SecurityContextHolder
                .getContext()
                .authentication
                ?.authorities
                ?.any { it.authority == "ROLE_USER" || it.authority == "ROLE_ADMIN" } == true

        val requests =
            // Only available for logged-in admins or users
            if (null != queryString && isAdminOrUser) {
                model.addAttribute("query", queryString)
                if (null != filter) {
                    model.addAttribute("filter", filter.value)
                    requestService
                        .searchRequestLike(PatientPseudonym(queryString), Tan(queryString), pageable)
                        .filter(filter)
                } else {
                    requestService
                        .searchRequestLike(PatientPseudonym(queryString), Tan(queryString), pageable)
                }

            } else {
                requestService.findAll(pageable)
            }

        model.addAttribute("requests", requests)
        model.addAttribute("postInitialSubmissionBlock", appConfigProperties.postInitialSubmissionBlock)
        return "index"
    }

    @GetMapping(path = ["patient/{patientPseudonym}"])
    fun byPatient(
        @PathVariable patientPseudonym: PatientPseudonym,
        @PageableDefault(page = 0, size = 10, sort = ["processedAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
        model: Model,
    ): String {
        val requests = requestService.findRequestByPatientId(patientPseudonym, pageable)
        model.addAttribute("patientPseudonym", patientPseudonym.value)
        model.addAttribute("requests", requests)
        model.addAttribute("postInitialSubmissionBlock", appConfigProperties.postInitialSubmissionBlock)
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
        model.addAttribute("postInitialSubmissionBlock", appConfigProperties.postInitialSubmissionBlock)

        return "report"
    }

    @PutMapping(path = ["/submission/{id}/accepted"])
    fun acceptSubmission(
        @PathVariable id: RequestId,
        model: Model,
    ): String {
        val request = requestService.findByUuid(id).orElse(null) ?: throw NotFoundException()
        request.submissionAccepted = true
        val savedRequest = requestService.save(request)

        model.addAttribute("request", savedRequest)
        model.addAttribute("postInitialSubmissionBlock", appConfigProperties.postInitialSubmissionBlock)

        return "fragments :: request"
    }

    @DeleteMapping(path = ["/submission/{id}/accepted"])
    fun unacceptSubmission(
        @PathVariable id: RequestId,
        model: Model,
    ): String {
        val request = requestService.findByUuid(id).orElse(null) ?: throw NotFoundException()
        request.submissionAccepted = false
        val savedRequest = requestService.save(request)

        model.addAttribute("request", savedRequest)
        model.addAttribute("postInitialSubmissionBlock", appConfigProperties.postInitialSubmissionBlock)

        return "fragments :: request"
    }

    @Component
    class FilterConverter : Converter<String, RequestService.Filter?> {
        override fun convert(source: String): RequestService.Filter? {
            return when (source) {
                "all-dip" -> RequestService.Filter.ALL_DIP
                "confirmed" -> RequestService.Filter.CONFIRMED
                "unconfirmed" -> RequestService.Filter.UNCONFIRMED
                else -> null
            }
        }
    }
}
