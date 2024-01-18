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
import dev.dnpm.etl.processor.services.Token
import dev.dnpm.etl.processor.services.TokenService
import dev.dnpm.etl.processor.services.TransformationService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks

@Controller
@RequestMapping(path = ["configs"])
class ConfigController(
    @Qualifier("configsUpdateProducer")
    private val configsUpdateProducer: Sinks.Many<Boolean>,
    private val transformationService: TransformationService,
    private val pseudonymGenerator: Generator,
    private val mtbFileSender: MtbFileSender,
    private val connectionCheckService: ConnectionCheckService,
    private val tokenService: TokenService?
) {

    @GetMapping
    fun index(model: Model): String {
        model.addAttribute("pseudonymGenerator", pseudonymGenerator.javaClass.simpleName)
        model.addAttribute("mtbFileSender", mtbFileSender.javaClass.simpleName)
        model.addAttribute("mtbFileEndpoint", mtbFileSender.endpoint())
        model.addAttribute("connectionAvailable", connectionCheckService.connectionAvailable())
        model.addAttribute("tokensEnabled", tokenService != null)
        if (tokenService != null) {
            model.addAttribute("tokens", tokenService.findAll())
        } else {
            model.addAttribute("tokens", listOf<Token>())
        }
        model.addAttribute("transformations", transformationService.getTransformations())

        return "configs"
    }

    @GetMapping(params = ["connectionAvailable"])
    fun connectionAvailable(model: Model): String {
        model.addAttribute("mtbFileSender", mtbFileSender.javaClass.simpleName)
        model.addAttribute("mtbFileEndpoint", mtbFileSender.endpoint())
        model.addAttribute("connectionAvailable", connectionCheckService.connectionAvailable())
        if (tokenService != null) {
            model.addAttribute("tokensEnabled", true)
            model.addAttribute("tokens", tokenService.findAll())
        } else {
            model.addAttribute("tokens", listOf<Token>())
        }

        return "configs/connectionAvailable"
    }

    @PostMapping(path = ["tokens"])
    fun addToken(@ModelAttribute("name") name: String, model: Model): String {
        if (tokenService == null) {
            model.addAttribute("tokensEnabled", false)
            model.addAttribute("success", false)
        } else {
            model.addAttribute("tokensEnabled", true)
            val result = tokenService.addToken(name)
            if (result.isSuccess) {
                model.addAttribute("newTokenValue", result.getOrDefault(""))
                model.addAttribute("success", true)
            } else {
                model.addAttribute("success", false)
            }
            model.addAttribute("tokens", tokenService.findAll())
        }

        return "configs/tokens"
    }

    @DeleteMapping(path = ["tokens/{id}"])
    fun deleteToken(@PathVariable id: Long, model: Model): String {
        if (tokenService != null) {
            tokenService.deleteToken(id)

            model.addAttribute("tokensEnabled", true)
            model.addAttribute("tokens", tokenService.findAll())
        } else {
            model.addAttribute("tokensEnabled", false)
            model.addAttribute("tokens", listOf<Token>())
        }
        return "configs/tokens"
    }

    @GetMapping(path = ["events"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(): Flux<ServerSentEvent<Any>> {
        return configsUpdateProducer.asFlux().map {
            ServerSentEvent.builder<Any>()
                .event("connection-available").id("none").data("")
                .build()
        }
    }

}