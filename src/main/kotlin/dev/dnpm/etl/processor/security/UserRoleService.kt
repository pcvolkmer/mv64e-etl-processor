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

package dev.dnpm.etl.processor.security

import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.core.session.SessionRegistry
import org.springframework.security.oauth2.core.oidc.user.OidcUser

class UserRoleService(
    private val userRoleRepository: UserRoleRepository,
    private val sessionRegistry: SessionRegistry
) {
    fun updateUserRole(id: Long, role: Role) {
        val userRole = userRoleRepository.findByIdOrNull(id) ?: return
        userRole.role = role
        userRoleRepository.save(userRole)
        expireSessionFor(userRole.username)
    }

    fun deleteUserRole(id: Long) {
        val userRole = userRoleRepository.findByIdOrNull(id) ?: return
        userRoleRepository.delete(userRole)
        expireSessionFor(userRole.username)
    }

    fun findAll(): List<UserRole> {
        return userRoleRepository.findAll().toList()
    }

    private fun expireSessionFor(username: String) {
        sessionRegistry.allPrincipals
            .filterIsInstance<OidcUser>()
            .filter { it.preferredUsername == username }
            .flatMap {
                sessionRegistry.getAllSessions(it, true)
            }
            .onEach {
                it.expireNow()
            }
    }
}