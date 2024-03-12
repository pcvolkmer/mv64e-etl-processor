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
import org.apache.commons.codec.digest.DigestUtils

/** Replaces patient ID with generated patient pseudonym
 *
 * @param pseudonymizeService The pseudonymizeService to be used
 *
 * @return The MTB file containing patient pseudonymes
 */
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
    this.responses.forEach { it.patient = patientPseudonym }
    this.studyInclusionRequests.forEach { it.patient = patientPseudonym }
    this.specimens.forEach { it.patient = patientPseudonym }
}

/**
 * Creates new hash of content IDs with given prefix except for patient IDs
 *
 * @param pseudonymizeService The pseudonymizeService to be used
 *
 * @return The MTB file containing rehashed content IDs
 */
infix fun MtbFile.anonymizeContentWith(pseudonymizeService: PseudonymizeService) {
    val prefix = pseudonymizeService.prefix()

    fun anonymize(id: String): String {
        val hash = DigestUtils.sha256Hex("$prefix-$id").substring(0, 41).lowercase()
        return "$prefix$hash"
    }

    this.episode.apply {
        id = anonymize(id)
    }
    this.carePlans.onEach { carePlan ->
        carePlan.apply {
            id = anonymize(id)
            diagnosis = anonymize(diagnosis)
            geneticCounsellingRequest = anonymize(geneticCounsellingRequest)
            rebiopsyRequests = rebiopsyRequests.map { anonymize(it) }
            recommendations = recommendations.map { anonymize(it) }
            studyInclusionRequests = studyInclusionRequests.map { anonymize(it) }
        }
    }
    this.claims.onEach { claim ->
        claim.apply {
            id = anonymize(id)
            therapy = anonymize(therapy)
        }
    }
    this.claimResponses.onEach { claimResponse ->
        claimResponse.apply {
            id = anonymize(id)
            claim = anonymize(claim)
        }
    }
    this.consent.apply {
        id = anonymize(id)
    }
    this.diagnoses.onEach { diagnosis ->
        diagnosis.apply {
            id = anonymize(id)
            histologyResults = histologyResults.map { anonymize(it) }
        }
    }
    this.ecogStatus.onEach { ecogStatus ->
        ecogStatus.apply {
            id = anonymize(id)
        }
    }
    this.familyMemberDiagnoses.onEach { familyMemberDiagnosis ->
        familyMemberDiagnosis.apply {
            id = anonymize(id)
        }
    }
    this.geneticCounsellingRequests.onEach { geneticCounsellingRequest -> 
        geneticCounsellingRequest.apply { 
            id = anonymize(id)
        }
    }
    this.histologyReevaluationRequests.onEach { histologyReevaluationRequest ->
        histologyReevaluationRequest.apply {
            id = anonymize(id)
            specimen = anonymize(specimen)
        }
    }
    this.histologyReports.onEach { histologyReport ->
        histologyReport.apply {
            id = anonymize(id)
            specimen = anonymize(specimen)
            tumorMorphology.apply {
                id = anonymize(id)
                specimen = anonymize(specimen)
            }
            tumorCellContent.apply {
                id = anonymize(id)
                specimen = anonymize(specimen)
            }
        }
    }
    this.lastGuidelineTherapies.onEach { lastGuidelineTherapy ->
        lastGuidelineTherapy.apply {
            id = anonymize(id)
            diagnosis = anonymize(diagnosis)
        }
    }
    this.molecularPathologyFindings.onEach { molecularPathologyFinding ->
        molecularPathologyFinding.apply {
            id = anonymize(id)
            specimen = anonymize(specimen)
        }
    }
    this.molecularTherapies.onEach { molecularTherapy ->
        molecularTherapy.apply {
            history.onEach { history ->
                history.apply {
                    id = anonymize(id)
                    basedOn = anonymize(basedOn)
                }
            }
        }
    }
    this.ngsReports.onEach { ngsReport ->
        ngsReport.apply {
            id = anonymize(id)
            specimen = anonymize(specimen)
            tumorCellContent.apply {
                id = anonymize(id)
                specimen = anonymize(specimen)
            }
            simpleVariants.onEach { simpleVariant ->
                simpleVariant.apply {
                    id = anonymize(id)
                }
            }
        }
    }
    this.previousGuidelineTherapies.onEach { previousGuidelineTherapy ->
        previousGuidelineTherapy.apply {
            id = anonymize(id)
            diagnosis = anonymize(diagnosis)
            this.medication.forEach { medication ->
                medication.apply {
                    id = anonymize(id)
                }
            }
        }
    }
    this.rebiopsyRequests.onEach { rebiopsyRequest ->
        rebiopsyRequest.apply {
            id = anonymize(id)
            specimen = anonymize(specimen)
        }
    }
    this.recommendations.onEach { recommendation ->
        recommendation.apply {
            id = anonymize(id)
            diagnosis = anonymize(diagnosis)
            ngsReport = anonymize(ngsReport)
        }
    }
    this.responses.onEach { response ->
        response.apply {
            id = anonymize(id)
            therapy = anonymize(therapy)
        }
    }
    this.studyInclusionRequests.onEach { studyInclusionRequest ->
        studyInclusionRequest.apply {
            id = anonymize(id)
            reason = anonymize(reason)
        }
    }
    this.specimens.onEach { specimen ->
        specimen.apply {
            id = anonymize(id)
        }
    }
}