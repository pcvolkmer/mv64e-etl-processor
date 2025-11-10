/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2025  Comprehensive Cancer Center Mainfranken, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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

import dev.dnpm.etl.processor.security.Role
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty

@ConfigurationProperties(AppConfigProperties.NAME)
data class AppConfigProperties(
    var transformations: List<TransformationProperties> = listOf(),
    var maxRetryAttempts: Int = 3,
    var duplicationDetection: Boolean = true,
    var genomDeTestSubmission: Boolean = false
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
    val soapEndpoint: String?,
    @get:DeprecatedConfigurationProperty(since = "0.12")
    val pidDomain: String?,
    val patientDomain: String = pidDomain ?: "etl-processor",
    val genomDeTanDomain: String = "ccdn",
    val username: String?,
    val password: String?,
) {
    companion object {
        const val NAME = "app.pseudonymize.gpas"
    }
}

@ConfigurationProperties(ConsentConfigProperties.NAME)
data class ConsentConfigProperties(
    var service: ConsentService = ConsentService.NONE
) {
    companion object {
        const val NAME = "app.consent"
    }
}

@ConfigurationProperties(GIcsConfigProperties.NAME)
data class GIcsConfigProperties(
    /**
     * Base URL to gICS System
     *
     */
    val uri: String?,
    val username: String? = null,
    val password: String? = null,

    /**
     * gICS specific system
     * **/
    val personIdentifierSystem: String =
        "https://ths-greifswald.de/fhir/gics/identifiers/Patienten-ID",

    /**
     * Domain of broad consent resources
     **/
    val broadConsentDomainName: String = "MII",

    /**
     * Domain of Modelvorhaben 64e consent resources
     **/
    val genomDeConsentDomainName: String = "GenomDE_MV",

    /**
     * Value to expect in case of positiv consent
     */
    val broadConsentPolicyCode: String = "2.16.840.1.113883.3.1937.777.24.5.3.6",

    /**
     * Consent Policy which should be used for consent check
     */
    val broadConsentPolicySystem: String = "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3",

    /**
     * Consent Policy uri for MII Broad Consent Version
     */
    val broadConsentPolicyUri: String = "urn:oid:2.16.840.1.113883.3.1937.777.24.2.1790",

    /**
     * Value to expect in case of positiv consent
     */
    val genomeDePolicyCode: String = "sequencing",

    /**
     * Consent Policy which should be used for consent check
     */
    val genomeDePolicySystem: String = "https://ths-greifswald.de/fhir/CodeSystem/gics/Policy/GenomDE_MV",

    /**
     * Consent version (fixed version)
     *
     */
    val genomeDeConsentVersion: String = "2.0"
) {
    companion object {
        const val NAME = "app.consent.gics"
    }
}

@ConfigurationProperties(RestTargetProperties.NAME)
data class RestTargetProperties(
    val uri: String?,
    val username: String?,
    val password: String?
) {
    companion object {
        const val NAME = "app.rest"
    }
}

@ConfigurationProperties(KafkaProperties.NAME)
data class KafkaProperties(
    val inputTopic: String?,
    val outputTopic: String = "etl-processor",
    val outputResponseTopic: String = "${outputTopic}_response",
    val groupId: String = "${outputTopic}_group",
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
    val enableTokens: Boolean = false,
    val enableOidc: Boolean = false,
    val defaultNewUserRole: Role = Role.USER
) {
    companion object {
        const val NAME = "app.security"
    }
}

enum class PseudonymGenerator {
    BUILDIN,
    GPAS
}

enum class ConsentService {
    NONE,
    GICS
}

data class TransformationProperties(
    val path: String,
    val from: String,
    val to: String
)
