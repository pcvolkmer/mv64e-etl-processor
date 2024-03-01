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

package dev.dnpm.etl.processor.config

import dev.dnpm.etl.processor.security.UserRole
import dev.dnpm.etl.processor.security.UserRoleRepository
import dev.dnpm.etl.processor.services.UserRoleService
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
import java.util.*


@Configuration
@EnableConfigurationProperties(
    value = [
        SecurityConfigProperties::class
    ]
)
@ConditionalOnProperty(value = ["app.security.admin-user"])
@EnableWebSecurity
class AppSecurityConfiguration(
    private val securityConfigProperties: SecurityConfigProperties
) {

    private val logger = LoggerFactory.getLogger(AppSecurityConfiguration::class.java)

    @Bean
    fun userDetailsService(passwordEncoder: PasswordEncoder): InMemoryUserDetailsManager {
        val adminUser = if (securityConfigProperties.adminUser.isNullOrBlank()) {
            logger.warn("Using random Admin User: admin")
            "admin"
        } else {
            securityConfigProperties.adminUser
        }

        val adminPassword = if (securityConfigProperties.adminPassword.isNullOrBlank()) {
            val random = UUID.randomUUID().toString()
            logger.warn("Using random Admin Passwort: {}", random)
            passwordEncoder.encode(random)
        } else {
            securityConfigProperties.adminPassword
        }

        val user: UserDetails = User.withUsername(adminUser)
            .password(adminPassword)
            .roles("ADMIN")
            .build()

        return InMemoryUserDetailsManager(user)
    }

    @Bean
    @ConditionalOnProperty(value = ["app.security.enable-oidc"], havingValue = "true")
    fun filterChainOidc(http: HttpSecurity, passwordEncoder: PasswordEncoder, userRoleRepository: UserRoleRepository, sessionRegistry: SessionRegistry): SecurityFilterChain {
        http {
            authorizeRequests {
                authorize("/configs/**", hasRole("ADMIN"))
                authorize("/mtbfile/**", hasAnyRole("MTBFILE"))
                authorize("/report/**", hasAnyRole("ADMIN", "USER"))
                authorize("*.css", permitAll)
                authorize("*.ico", permitAll)
                authorize("*.jpeg", permitAll)
                authorize("*.js", permitAll)
                authorize("*.svg", permitAll)
                authorize("*.css", permitAll)
                authorize("/login/**", permitAll)
                authorize(anyRequest, permitAll)
            }
            httpBasic {
                realmName = "ETL-Processor"
            }
            formLogin {
                loginPage = "/login"
            }
            oauth2Login {
                loginPage = "/login"
            }
            sessionManagement {
                sessionConcurrency {
                    maximumSessions = 1
                    maxSessionsPreventsLogin = true
                    expiredUrl = "/login?expired"
                }
                sessionFixation {
                    newSession()
                }
            }
            csrf { disable() }
        }
        return http.build()
    }

    @Bean
    @ConditionalOnProperty(value = ["app.security.enable-oidc"], havingValue = "true")
    fun grantedAuthoritiesMapper(userRoleRepository: UserRoleRepository, appSecurityConfigProperties: SecurityConfigProperties): GrantedAuthoritiesMapper {
        return GrantedAuthoritiesMapper { grantedAuthority ->
            grantedAuthority.filterIsInstance<OidcUserAuthority>()
                .onEach {
                    val userRole = userRoleRepository.findByUsername(it.userInfo.preferredUsername)
                    if (userRole.isEmpty) {
                        userRoleRepository.save(UserRole(null, it.userInfo.preferredUsername, appSecurityConfigProperties.defaultNewUserRole))
                    }
                }
                .map {
                    val userRole = userRoleRepository.findByUsername(it.userInfo.preferredUsername)
                    SimpleGrantedAuthority("ROLE_${userRole.get().role.toString().uppercase()}")
                }
        }
    }

    @Bean
    @ConditionalOnProperty(value = ["app.security.enable-oidc"], havingValue = "false", matchIfMissing = true)
    fun filterChain(http: HttpSecurity, passwordEncoder: PasswordEncoder): SecurityFilterChain {
        http {
            authorizeRequests {
                authorize("/configs/**", hasRole("ADMIN"))
                authorize("/mtbfile/**", hasAnyRole("MTBFILE"))
                authorize("/report/**", hasRole("ADMIN"))
                authorize(anyRequest, permitAll)
            }
            httpBasic {
                realmName = "ETL-Processor"
            }
            formLogin {
                loginPage = "/login"
            }
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
    fun userRoleService(userRoleRepository: UserRoleRepository, sessionRegistry: SessionRegistry): UserRoleService {
        return UserRoleService(userRoleRepository, sessionRegistry)
    }
}
