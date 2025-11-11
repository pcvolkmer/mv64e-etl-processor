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

package dev.dnpm.etl.processor.web

import dev.dnpm.etl.processor.config.SecurityConfigProperties
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class LoginController(
    private val securityConfigProperties: SecurityConfigProperties?,
    private val oAuth2ClientProperties: OAuth2ClientProperties?,
) {
    @GetMapping(path = ["/login"])
    fun login(model: Model): String {
        if (securityConfigProperties?.enableOidc == true) {
            model.addAttribute(
                "oidcLogins",
                oAuth2ClientProperties
                    ?.registration
                    ?.map { (key, value) -> Pair(key, value.clientName) }
                    .orEmpty(),
            )
        } else {
            model.addAttribute("oidcLogins", emptyList<Pair<String, String>>())
        }
        return "login"
    }
}
