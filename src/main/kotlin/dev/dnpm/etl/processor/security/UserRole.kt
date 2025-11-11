/*
 * This file is part of ETL-Processor
 *
 * Copyright (C) 2024  Comprehensive Cancer Center Mainfranken, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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
 *
 */

package dev.dnpm.etl.processor.security

import java.util.*
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository

@Table("user_role")
data class UserRole(@Id val id: Long? = null, val username: String, var role: Role = Role.GUEST)

enum class Role(val value: String) {
  GUEST("guest"),
  USER("user"),
  ADMIN("admin"),
}

interface UserRoleRepository : CrudRepository<UserRole, Long> {

  fun findByUsername(username: String): Optional<UserRole>
}
