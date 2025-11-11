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

import jakarta.annotation.PostConstruct
import java.time.Instant
import java.util.*
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager

class TokenService(
    private val userDetailsManager: InMemoryUserDetailsManager,
    private val passwordEncoder: PasswordEncoder,
    private val tokenRepository: TokenRepository,
) {

  @PostConstruct
  fun setup() {
    tokenRepository.findAll().forEach {
      userDetailsManager.createUser(
          User.withUsername(it.username).password(it.password).roles("MTBFILE").build()
      )
    }
  }

  fun addToken(name: String): Result<String> {
    val username = name.lowercase().replace("""[^a-z0-9]""".toRegex(), "")
    if (userDetailsManager.userExists(username)) {
      return Result.failure(RuntimeException("Cannot use token name"))
    }

    val password =
        Base64.getEncoder().encodeToString(UUID.randomUUID().toString().encodeToByteArray())
    val encodedPassword = passwordEncoder.encode(password).toString()

    userDetailsManager.createUser(
        User.withUsername(username).password(encodedPassword).roles("MTBFILE").build()
    )

    tokenRepository.save(Token(name = name, username = username, password = encodedPassword))

    return Result.success("$username:$password")
  }

  fun deleteToken(id: Long) {
    val token = tokenRepository.findByIdOrNull(id) ?: return
    userDetailsManager.deleteUser(token.username)
    tokenRepository.delete(token)
  }

  fun findAll(): List<Token> {
    return tokenRepository.findAll().toList()
  }
}

@Table("token")
data class Token(
    @Id val id: Long? = null,
    val name: String,
    val username: String,
    val password: String,
    val createdAt: Instant = Instant.now(),
)

interface TokenRepository : CrudRepository<Token, Long>
