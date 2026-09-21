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

import dev.dnpm.etl.processor.monitoring.ReportService
import dev.dnpm.etl.processor.output.RoutedMtbFileSender
import dev.dnpm.etl.processor.output.RoutedRestNngmMtbFileSender
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.retry.support.RetryTemplate
import org.springframework.web.client.RestTemplate

@Configuration
@EnableConfigurationProperties(value = [RoutingProperties::class])
@Order(-10)
class AppRoutedRestConfiguration {
    private val logger = LoggerFactory.getLogger(AppRoutedRestConfiguration::class.java)

    @Bean
    @ConditionalOnProperty(prefix = "app.routing", name = ["nngm.uri"])
    fun switchRestMtbFileSender(
        restTemplate: RestTemplate,
        routingProperties: RoutingProperties,
        retryTemplate: RetryTemplate,
        reportService: ReportService,
    ): RoutedMtbFileSender {
        logger.info("Added switched 'RestNngmMtbFileSender' ... ")
        return RoutedRestNngmMtbFileSender(restTemplate, routingProperties, retryTemplate, reportService)
    }
}
