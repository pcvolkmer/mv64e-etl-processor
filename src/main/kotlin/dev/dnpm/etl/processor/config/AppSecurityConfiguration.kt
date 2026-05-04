/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2023       Comprehensive Cancer Center Mainfranken
 * Copyright (c) 2025-2026  Paul-Christian Volkmer, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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

package dev.dnpm.etl.processor.config

import dev.dnpm.etl.processor.security.UserRole
import dev.dnpm.etl.processor.security.UserRoleRepository
import dev.dnpm.etl.processor.security.UserRoleService
import java.util.*
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper
import org.springframework.security.core.session.SessionRegistry
import org.springframework.security.core.session.SessionRegistryImpl
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

private const val LOGIN_PATH = "/login"

@Configuration
@EnableConfigurationProperties(value = [SecurityConfigProperties::class])
@ConditionalOnProperty(value = ["app.security.admin-user"])
@EnableWebSecurity
class AppSecurityConfiguration(private val securityConfigProperties: SecurityConfigProperties) {

  private val logger = LoggerFactory.getLogger(AppSecurityConfiguration::class.java)

    private fun authorizeAppRequests(http: HttpSecurity) {
        http {
            authorizeHttpRequests {
                authorize("/configs/**", hasRole("ADMIN"))
                authorize("/api/mtbfile/**", hasAnyRole("MTBFILE", "ADMIN", "USER"))
                authorize("/api/mtb/**", hasAnyRole("MTBFILE", "ADMIN", "USER"))
                authorize("/mtbfile/**", hasAnyRole("MTBFILE", "ADMIN", "USER"))
                authorize("/mtb/**", hasAnyRole("MTBFILE", "ADMIN", "USER"))
                authorize("/patient/**", hasAnyRole("ADMIN", "USER"))
                authorize("/report/**", hasAnyRole("ADMIN", "USER"))
                authorize("/submission/**", hasAnyRole("ADMIN", "USER"))
                authorize(anyRequest, permitAll)
            }
        }
     }

  @Bean
  fun userDetailsService(passwordEncoder: PasswordEncoder): InMemoryUserDetailsManager {
    val adminUser =
        if (securityConfigProperties.adminUser.isNullOrBlank()) {
          logger.warn("Using random Admin User: admin")
          "admin"
        } else {
          securityConfigProperties.adminUser
        }

    val adminPassword =
        if (securityConfigProperties.adminPassword.isNullOrBlank()) {
          val random = UUID.randomUUID().toString()
          logger.warn("Using random Admin Passwort: {}", random)
          passwordEncoder.encode(random)
        } else {
          securityConfigProperties.adminPassword
        }

    val admin: UserDetails =
        User.withUsername(adminUser).password(adminPassword).roles("ADMIN").build()

    val users = securityConfigProperties.users.map {
        User.withUsername(it.username).password(it.password).roles("USER").build()
    }.toTypedArray()

    return InMemoryUserDetailsManager(admin, *users)
  }

  @Bean
  @ConditionalOnProperty(value = ["app.security.enable-oidc"], havingValue = "true")
  fun filterChainOidc(
      http: HttpSecurity,
      passwordEncoder: PasswordEncoder,
      userRoleRepository: UserRoleRepository,
      sessionRegistry: SessionRegistry,
  ): SecurityFilterChain {
    authorizeAppRequests(http)
    http {
      httpBasic { realmName = "ETL-Processor" }
      formLogin { loginPage = LOGIN_PATH }
      oauth2Login { loginPage = LOGIN_PATH }
      sessionManagement {
        sessionConcurrency {
          maximumSessions = 1
          expiredUrl = "$LOGIN_PATH?expired"
        }
        sessionFixation { newSession() }
      }
      csrf { disable() }
    }
    return http.build()
  }

  @Bean
  @ConditionalOnProperty(value = ["app.security.enable-oidc"], havingValue = "true")
  fun grantedAuthoritiesMapper(
      userRoleRepository: UserRoleRepository,
      appSecurityConfigProperties: SecurityConfigProperties,
  ): GrantedAuthoritiesMapper {
    return GrantedAuthoritiesMapper { grantedAuthority ->
      grantedAuthority
          .filterIsInstance<OidcUserAuthority>()
          .onEach {
            val userRole = userRoleRepository.findByUsername(it.userInfo.preferredUsername)
            if (userRole.isEmpty) {
              userRoleRepository.save(
                  UserRole(
                      null,
                      it.userInfo.preferredUsername,
                      appSecurityConfigProperties.defaultNewUserRole,
                  )
              )
            }
          }
          .map {
            val userRole = userRoleRepository.findByUsername(it.userInfo.preferredUsername)
            SimpleGrantedAuthority("ROLE_${userRole.get().role.toString().uppercase()}")
          }
    }
  }

  @Bean
  @ConditionalOnProperty(
      value = ["app.security.enable-oidc"],
      havingValue = "false",
      matchIfMissing = true,
  )
  fun filterChain(http: HttpSecurity, passwordEncoder: PasswordEncoder): SecurityFilterChain {
    authorizeAppRequests(http)
    http {
      httpBasic { realmName = "ETL-Processor" }
      formLogin { loginPage = LOGIN_PATH }
      csrf { disable() }
    }
    return http.build()
  }

  @Bean
  fun sessionRegistry(): SessionRegistry {
    return SessionRegistryImpl()
  }

  @Bean
  fun passwordEncoder(): PasswordEncoder {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder()
  }

  @Bean
  @ConditionalOnProperty(value = ["app.security.enable-oidc"], havingValue = "true")
  fun userRoleService(
      userRoleRepository: UserRoleRepository,
      sessionRegistry: SessionRegistry,
  ): UserRoleService {
    return UserRoleService(userRoleRepository, sessionRegistry)
  }
}
