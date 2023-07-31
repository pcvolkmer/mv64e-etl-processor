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

package dev.dnpm.etl.processor.pseudonym

import de.ukw.ccc.bwhc.dto.MtbFile
import dev.dnpm.etl.processor.config.PseudonymizeConfigProperties

class PseudonymizeService(
    private val generator: Generator,
    private val configProperties: PseudonymizeConfigProperties
) {

    fun pseudonymize(mtbFile: MtbFile): MtbFile {
        val patientPseudonym = patientPseudonym(mtbFile.patient.id)

        mtbFile.episode.patient = patientPseudonym
        mtbFile.carePlans.forEach { it.patient = patientPseudonym }
        mtbFile.patient.id = patientPseudonym
        mtbFile.claims.forEach { it.patient = patientPseudonym }
        mtbFile.consent.patient = patientPseudonym
        mtbFile.claimResponses.forEach { it.patient = patientPseudonym }
        mtbFile.diagnoses.forEach { it.patient = patientPseudonym }
        mtbFile.ecogStatus.forEach { it.patient = patientPseudonym }
        mtbFile.familyMemberDiagnoses.forEach { it.patient = patientPseudonym }
        mtbFile.geneticCounsellingRequests.forEach { it.patient = patientPseudonym }
        mtbFile.histologyReevaluationRequests.forEach { it.patient = patientPseudonym }
        mtbFile.histologyReports.forEach { it.patient = patientPseudonym }
        mtbFile.lastGuidelineTherapies.forEach { it.patient = patientPseudonym }
        mtbFile.molecularPathologyFindings.forEach { it.patient = patientPseudonym }
        mtbFile.molecularTherapies.forEach { it.history.forEach { it.patient = patientPseudonym } }
        mtbFile.ngsReports.forEach { it.patient = patientPseudonym }
        mtbFile.previousGuidelineTherapies.forEach { it.patient = patientPseudonym }
        mtbFile.rebiopsyRequests.forEach { it.patient = patientPseudonym }
        mtbFile.recommendations.forEach { it.patient = patientPseudonym }
        mtbFile.recommendations.forEach { it.patient = patientPseudonym }
        mtbFile.responses.forEach { it.patient = patientPseudonym }
        mtbFile.specimens.forEach { it.patient = patientPseudonym }
        mtbFile.specimens.forEach { it.patient = patientPseudonym }

        return mtbFile
    }

    fun patientPseudonym(patientId: String): String {
        return "${configProperties.prefix}_${generator.generate(patientId)}"
    }

}