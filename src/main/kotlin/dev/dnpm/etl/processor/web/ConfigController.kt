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

import dev.dnpm.etl.processor.monitoring.ConnectionCheckResult
import dev.dnpm.etl.processor.monitoring.ConnectionCheckService
import dev.dnpm.etl.processor.monitoring.GPasConnectionCheckService
import dev.dnpm.etl.processor.monitoring.OutputConnectionCheckService
import dev.dnpm.etl.processor.output.MtbFileSender
import dev.dnpm.etl.processor.pseudonym.Generator
import dev.dnpm.etl.processor.security.Role
import dev.dnpm.etl.processor.security.UserRole
import dev.dnpm.etl.processor.security.Token
import dev.dnpm.etl.processor.security.TokenService
import dev.dnpm.etl.processor.services.TransformationService
import dev.dnpm.etl.processor.security.UserRoleService
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
    @Qualifier("connectionCheckUpdateProducer")
    private val connectionCheckUpdateProducer: Sinks.Many<ConnectionCheckResult>,
    private val transformationService: TransformationService,
    private val pseudonymGenerator: Generator,
    private val mtbFileSender: MtbFileSender,
    private val connectionCheckServices: List<ConnectionCheckService>,
    private val tokenService: TokenService?,
    private val userRoleService: UserRoleService?
) {

    @GetMapping
    fun index(model: Model): String {
        val outputConnectionAvailable =
            connectionCheckServices.filterIsInstance<OutputConnectionCheckService>().firstOrNull()?.connectionAvailable()

        val gPasConnectionAvailable =
            connectionCheckServices.filterIsInstance<GPasConnectionCheckService>().firstOrNull()?.connectionAvailable()

        model.addAttribute("pseudonymGenerator", pseudonymGenerator.javaClass.simpleName)
        model.addAttribute("mtbFileSender", mtbFileSender.javaClass.simpleName)
        model.addAttribute("mtbFileEndpoint", mtbFileSender.endpoint())
        model.addAttribute("outputConnectionAvailable", outputConnectionAvailable)
        model.addAttribute("gPasConnectionAvailable", gPasConnectionAvailable)
        model.addAttribute("tokensEnabled", tokenService != null)
        if (tokenService != null) {
            model.addAttribute("tokens", tokenService.findAll())
        } else {
            model.addAttribute("tokens", emptyList<Token>())
        }
        model.addAttribute("transformations", transformationService.getTransformations())
        if (userRoleService != null) {
            model.addAttribute("userRolesEnabled", true)
            model.addAttribute("userRoles", userRoleService.findAll())
        } else {
            model.addAttribute("userRolesEnabled", false)
            model.addAttribute("userRoles", emptyList<UserRole>())
        }
        return "configs"
    }

    @GetMapping(params = ["outputConnectionAvailable"])
    fun outputConnectionAvailable(model: Model): String {
        val outputConnectionAvailable =
            connectionCheckServices.filterIsInstance<OutputConnectionCheckService>().first().connectionAvailable()

        model.addAttribute("mtbFileSender", mtbFileSender.javaClass.simpleName)
        model.addAttribute("mtbFileEndpoint", mtbFileSender.endpoint())
        model.addAttribute("outputConnectionAvailable", outputConnectionAvailable)
        if (tokenService != null) {
            model.addAttribute("tokensEnabled", true)
            model.addAttribute("tokens", tokenService.findAll())
        } else {
            model.addAttribute("tokens", listOf<Token>())
        }

        return "configs/outputConnectionAvailable"
    }

    @GetMapping(params = ["gPasConnectionAvailable"])
    fun gPasConnectionAvailable(model: Model): String {
        val gPasConnectionAvailable =
            connectionCheckServices.filterIsInstance<GPasConnectionCheckService>().firstOrNull()?.connectionAvailable()

        model.addAttribute("mtbFileSender", mtbFileSender.javaClass.simpleName)
        model.addAttribute("mtbFileEndpoint", mtbFileSender.endpoint())
        model.addAttribute("gPasConnectionAvailable", gPasConnectionAvailable)
        if (tokenService != null) {
            model.addAttribute("tokensEnabled", true)
            model.addAttribute("tokens", tokenService.findAll())
        } else {
            model.addAttribute("tokens", listOf<Token>())
        }

        return "configs/gPasConnectionAvailable"
    }

    @PostMapping(path = ["tokens"])
    fun addToken(@ModelAttribute("name") name: String, model: Model): String {
        if (tokenService == null) {
            model.addAttribute("tokensEnabled", false)
            model.addAttribute("success", false)
        } else {
            model.addAttribute("tokensEnabled", true)
            val result = tokenService.addToken(name)
            result.onSuccess {
                model.addAttribute("newTokenValue", it)
                model.addAttribute("success", true)
            }
            result.onFailure {
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

    @DeleteMapping(path = ["userroles/{id}"])
    fun deleteUserRole(@PathVariable id: Long, model: Model): String {
        if (userRoleService != null) {
            userRoleService.deleteUserRole(id)

            model.addAttribute("userRolesEnabled", true)
            model.addAttribute("userRoles", userRoleService.findAll())
        } else {
            model.addAttribute("userRolesEnabled", false)
            model.addAttribute("userRoles", emptyList<UserRole>())
        }
        return "configs/userroles"
    }

    @PutMapping(path = ["userroles/{id}"])
    fun updateUserRole(@PathVariable id: Long, @ModelAttribute("role") role: Role, model: Model): String {
        if (userRoleService != null) {
            userRoleService.updateUserRole(id, role)

            model.addAttribute("userRolesEnabled", true)
            model.addAttribute("userRoles", userRoleService.findAll())
        } else {
            model.addAttribute("userRolesEnabled", false)
            model.addAttribute("userRoles", emptyList<UserRole>())
        }
        return "configs/userroles"
    }

    @GetMapping(path = ["events"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(): Flux<ServerSentEvent<Any>> {
        return connectionCheckUpdateProducer.asFlux().map {
            val event = when (it) {
                is ConnectionCheckResult.KafkaConnectionCheckResult -> "output-connection-check"
                is ConnectionCheckResult.RestConnectionCheckResult -> "output-connection-check"
                is ConnectionCheckResult.GPasConnectionCheckResult -> "gpas-connection-check"
            }

            ServerSentEvent.builder<Any>()
                .event(event).id("none").data(it)
                .build()
        }
    }

}