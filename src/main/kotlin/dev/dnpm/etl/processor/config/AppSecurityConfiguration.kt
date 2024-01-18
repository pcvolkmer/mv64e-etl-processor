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

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy
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
    fun filterChain(http: HttpSecurity, passwordEncoder: PasswordEncoder): SecurityFilterChain {
        http {
            authorizeRequests {
                authorize("/configs/**", hasRole("ADMIN"))
                authorize("/mtbfile/**", hasAnyRole("MTBFILE"))
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
    fun passwordEncoder(): PasswordEncoder {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder()
    }

}

