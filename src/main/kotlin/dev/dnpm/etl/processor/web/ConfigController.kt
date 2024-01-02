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

import dev.dnpm.etl.processor.monitoring.ConnectionCheckService
import dev.dnpm.etl.processor.output.MtbFileSender
import dev.dnpm.etl.processor.pseudonym.Generator
import dev.dnpm.etl.processor.services.TransformationService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping(path = ["configs"])
class ConfigController(
    private val transformationService: TransformationService,
    private val pseudonymGenerator: Generator,
    private val mtbFileSender: MtbFileSender,
    private val connectionCheckService: ConnectionCheckService

) {

    @GetMapping
    fun index(model: Model): String {
        model.addAttribute("pseudonymGenerator", pseudonymGenerator.javaClass.simpleName)
        model.addAttribute("mtbFileSender", mtbFileSender.javaClass.simpleName)
        model.addAttribute("connectionAvailable", connectionCheckService.connectionAvailable())
        model.addAttribute("transformations", transformationService.getTransformations())

        return "configs"
    }

}