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

package dev.dnpm.etl.processor.pseudonym

import dev.dnpm.etl.processor.config.AppFhirConfig
import dev.dnpm.etl.processor.config.GPasConfigProperties
import org.springframework.retry.support.RetryTemplate

class GpasSoapPseudonymGenerator(
    private val gpasCfg: GPasConfigProperties,
    private val retryTemplate: RetryTemplate,
    private val gpasSoapService: GpasSoapService,
    private val appFhirConfig: AppFhirConfig,
) : Generator {
    override fun generate(id: String): String =
        retryTemplate.execute<String, Exception> {
            gpasSoapService.getOrCreatePseudonymFor(id, gpasCfg.patientDomain)
        }

    override fun generateGenomDeTan(id: String): String =
        retryTemplate.execute<String, Exception> {
            gpasSoapService.createPseudonymsFor(id, gpasCfg.genomDeTanDomain, 1).first()
        }
}
