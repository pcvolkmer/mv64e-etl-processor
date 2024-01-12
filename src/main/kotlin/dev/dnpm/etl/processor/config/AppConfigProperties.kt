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

package dev.dnpm.etl.processor.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty

@ConfigurationProperties(AppConfigProperties.NAME)
data class AppConfigProperties(
    var bwhcUri: String?,
    @get:DeprecatedConfigurationProperty(
        reason = "Deprecated in favor of 'app.pseudonymize.generator'",
        replacement = "app.pseudonymize.generator"
    )
    var pseudonymizer: PseudonymGenerator = PseudonymGenerator.BUILDIN,
    var transformations: List<TransformationProperties> = listOf()
) {
    companion object {
        const val NAME = "app"
    }
}

@ConfigurationProperties(PseudonymizeConfigProperties.NAME)
data class PseudonymizeConfigProperties(
    var generator: PseudonymGenerator = PseudonymGenerator.BUILDIN,
    val prefix: String = "UNKNOWN",
) {
    companion object {
        const val NAME = "app.pseudonymize"
    }
}

@ConfigurationProperties(GPasConfigProperties.NAME)
data class GPasConfigProperties(
    val uri: String?,
    val target: String = "etl-processor",
    val username: String?,
    val password: String?,
    val sslCaLocation: String?,

    ) {
    companion object {
        const val NAME = "app.pseudonymize.gpas"
    }
}

@ConfigurationProperties(RestTargetProperties.NAME)
data class RestTargetProperties(
    val uri: String?,
) {
    companion object {
        const val NAME = "app.rest"
    }
}

@ConfigurationProperties(KafkaTargetProperties.NAME)
data class KafkaTargetProperties(
    val topic: String = "etl-processor",
    val responseTopic: String = "${topic}_response",
    val groupId: String = "${topic}_group",
    val servers: String = ""
) {
    companion object {
        const val NAME = "app.kafka"
    }
}

@ConfigurationProperties(SecurityConfigProperties.NAME)
data class SecurityConfigProperties(
    val adminUser: String?,
    val adminPassword: String?,
) {
    companion object {
        const val NAME = "app.security"
    }
}

enum class PseudonymGenerator {
    BUILDIN,
    GPAS
}

data class TransformationProperties(
    val path: String,
    val from: String,
    val to: String
)