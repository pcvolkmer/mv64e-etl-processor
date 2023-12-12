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

infix fun MtbFile.pseudonymizeWith(pseudonymizeService: PseudonymizeService) {
    val patientPseudonym = pseudonymizeService.patientPseudonym(this.patient.id)

    this.episode.patient = patientPseudonym
    this.carePlans.forEach { it.patient = patientPseudonym }
    this.patient.id = patientPseudonym
    this.claims.forEach { it.patient = patientPseudonym }
    this.consent.patient = patientPseudonym
    this.claimResponses.forEach { it.patient = patientPseudonym }
    this.diagnoses.forEach { it.patient = patientPseudonym }
    this.ecogStatus.forEach { it.patient = patientPseudonym }
    this.familyMemberDiagnoses.forEach { it.patient = patientPseudonym }
    this.geneticCounsellingRequests.forEach { it.patient = patientPseudonym }
    this.histologyReevaluationRequests.forEach { it.patient = patientPseudonym }
    this.histologyReports.forEach {
        it.patient = patientPseudonym
        it.tumorMorphology.patient = patientPseudonym
    }
    this.lastGuidelineTherapies.forEach { it.patient = patientPseudonym }
    this.molecularPathologyFindings.forEach { it.patient = patientPseudonym }
    this.molecularTherapies.forEach { molecularTherapy -> molecularTherapy.history.forEach { it.patient = patientPseudonym } }
    this.ngsReports.forEach { it.patient = patientPseudonym }
    this.previousGuidelineTherapies.forEach { it.patient = patientPseudonym }
    this.rebiopsyRequests.forEach { it.patient = patientPseudonym }
    this.recommendations.forEach { it.patient = patientPseudonym }
    this.recommendations.forEach { it.patient = patientPseudonym }
    this.responses.forEach { it.patient = patientPseudonym }
    this.studyInclusionRequests.forEach { it.patient = patientPseudonym }
    this.specimens.forEach { it.patient = patientPseudonym }
}