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

package dev.dnpm.etl.processor.output

import dev.dnpm.etl.processor.PatientPseudonym
import dev.dnpm.etl.processor.config.RestTargetProperties
import org.springframework.retry.support.RetryTemplate
import org.springframework.web.client.RestTemplate

class RestBwhcMtbFileSender(
    private val restTemplate: RestTemplate,
    private val restTargetProperties: RestTargetProperties,
    private val retryTemplate: RetryTemplate
) : RestMtbFileSender(restTemplate, restTargetProperties, retryTemplate) {

    override fun sendUrl(): String {
        return "${restTargetProperties.uri}/MTBFile"
    }

    override fun deleteUrl(patientId: PatientPseudonym): String {
        return "${restTargetProperties.uri}/Patient/${patientId.value}"
    }

}