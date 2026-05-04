/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2023       Comprehensive Cancer Center Mainfranken
 * Copyright (c) 2026  Paul-Christian Volkmer, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.test.context.ContextConfiguration

@SpringBootTest
@ContextConfiguration(
    classes =
        [
            GPasConfigProperties::class,
        ],
)
class GPasConfigPropertiesTest {
    @EnableConfigurationProperties(GPasConfigProperties::class)
    class TestConfig

    @Test
    fun shouldUseConfiguredPatientDomainIfPidDomainGiven() {
        ApplicationContextRunner()
            .withUserConfiguration(TestConfig::class.java)
            .withPropertyValues(
                "app.pseudonymize.gpas.uri=http://localhost/",
                "app.pseudonymize.gpas.pid-domain=test-pid-domain",
            ).run { context ->
                val properties = context.getBean(GPasConfigProperties::class.java)

                assertThat(properties).isNotNull
                assertThat(properties.patientDomain).isEqualTo("test-pid-domain")
            }
    }

    @Test
    fun shouldUseConfiguredPatientDomain() {
        ApplicationContextRunner()
            .withUserConfiguration(TestConfig::class.java)
            .withPropertyValues(
                "app.pseudonymize.gpas.uri=http://localhost/",
                "app.pseudonymize.gpas.patient-domain=test-patient-domain",
            ).run { context ->
                val properties = context.getBean(GPasConfigProperties::class.java)

                assertThat(properties).isNotNull
                assertThat(properties.patientDomain).isEqualTo("test-patient-domain")
            }
    }
}
