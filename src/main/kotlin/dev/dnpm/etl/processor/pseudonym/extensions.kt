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

package dev.dnpm.etl.processor.pseudonym

import dev.dnpm.etl.processor.PatientId
import dev.pcvolkmer.mv64e.mtb.ModelProjectConsent
import dev.pcvolkmer.mv64e.mtb.Mtb
import dev.pcvolkmer.mv64e.mtb.MvhMetadata
import org.apache.commons.codec.digest.DigestUtils

/** Replaces patient ID with generated patient pseudonym
 *
 * @since 0.11.0
 *
 * @param pseudonymizeService The pseudonymizeService to be used
 * @return The MTB file containing patient pseudonymes
 */
infix fun Mtb.pseudonymizeWith(pseudonymizeService: PseudonymizeService) {
    val patientPseudonym = pseudonymizeService.patientPseudonym(PatientId(this.patient.id)).value

    this.episodesOfCare?.forEach { it.patient.id = patientPseudonym }
    this.carePlans?.forEach {
        it.patient.id = patientPseudonym
        it.rebiopsyRequests?.forEach { it.patient.id = patientPseudonym }
        it.histologyReevaluationRequests?.forEach { it.patient.id = patientPseudonym }
        it.medicationRecommendations.forEach { it.patient.id = patientPseudonym }
        it.studyEnrollmentRecommendations?.forEach { it.patient.id = patientPseudonym }
        it.procedureRecommendations?.forEach { it.patient.id = patientPseudonym }
        it.geneticCounselingRecommendation.patient.id = patientPseudonym
    }
    this.diagnoses?.forEach { it.patient.id = patientPseudonym }
    this.guidelineTherapies?.forEach { it.patient.id = patientPseudonym }
    this.guidelineProcedures?.forEach { it.patient.id = patientPseudonym }
    this.patient.id = patientPseudonym
    this.claims?.forEach { it.patient.id = patientPseudonym }
    this.claimResponses?.forEach { it.patient.id = patientPseudonym }
    this.diagnoses?.forEach { it.patient.id = patientPseudonym }
    this.familyMemberHistories?.forEach { it.patient.id = patientPseudonym }
    this.histologyReports?.forEach {
        it.patient.id = patientPseudonym
        it.results.tumorMorphology?.patient?.id = patientPseudonym
        it.results.tumorCellContent?.patient?.id = patientPseudonym
    }
    this.ngsReports?.forEach {
        it.patient.id = patientPseudonym
        it.results.simpleVariants?.forEach { it.patient.id = patientPseudonym }
        it.results.copyNumberVariants?.forEach { it.patient.id = patientPseudonym }
        it.results.dnaFusions?.forEach { it.patient.id = patientPseudonym }
        it.results.rnaFusions?.forEach { it.patient.id = patientPseudonym }
        it.results.tumorCellContent?.patient?.id = patientPseudonym
        it.results.brcaness?.patient?.id = patientPseudonym
        it.results.tmb?.patient?.id = patientPseudonym
        it.results.hrdScore?.patient?.id = patientPseudonym
    }
    this.ihcReports?.forEach {
        it.patient.id = patientPseudonym
        it.results.msiMmr?.forEach { it.patient.id = patientPseudonym }
        it.results.proteinExpression?.forEach { it.patient.id = patientPseudonym }
    }
    this.responses?.forEach { it.patient.id = patientPseudonym }
    this.specimens?.forEach { it.patient.id = patientPseudonym }
    this.priorDiagnosticReports?.forEach { it.patient.id = patientPseudonym }
    this.performanceStatus?.forEach { it.patient.id = patientPseudonym }
    this.systemicTherapies?.forEach {
        it.history?.forEach {
            it.patient.id = patientPseudonym
        }
    }
    this.followUps?.forEach {
        it.patient.id = patientPseudonym
    }

    this.msiFindings?.forEach { it -> it.patient.id = patientPseudonym }

    this.metadata?.researchConsents?.forEach { it ->
        val entry = it ?: return@forEach
        if (entry.contains("patient")) {
            // here we expect only a patient reference any other data like display
            // need to be removed, since may contain unsecure data
            entry.remove("patient")
            entry["patient"] = mapOf("reference" to "Patient/$patientPseudonym")
        }
    }
}

/**
 * Creates new hash of content IDs with given prefix except for patient IDs
 *
 * @since 0.11.0
 *
 * @param pseudonymizeService The pseudonymizeService to be used
 * @return The MTB file containing rehashed content IDs
 */
infix fun Mtb.anonymizeContentWith(pseudonymizeService: PseudonymizeService) {
    val prefix = pseudonymizeService.prefix()

    fun anonymize(id: String): String {
        val hash = DigestUtils.sha256Hex("$prefix-$id").substring(0, 41).lowercase()
        return "$prefix$hash"
    }

    this.episodesOfCare?.forEach {
        it?.apply { id = id?.let(::anonymize) }
        it.diagnoses.forEach { it ->
            it?.id = it.id?.let(::anonymize)
        }
    }

    this.carePlans?.onEach { carePlan ->
        carePlan?.apply {
            id = id?.let { anonymize(it) }

            diagnoses?.forEach { it -> it?.id = it.id?.let(::anonymize) }
            geneticCounselingRecommendation?.apply {
                id = geneticCounselingRecommendation.id?.let(::anonymize)
            }
            rebiopsyRequests?.forEach { it ->
                it.id = it.id?.let(::anonymize)
                it.tumorEntity?.id = it.tumorEntity?.id?.let(::anonymize)
            }
            histologyReevaluationRequests?.forEach { it ->
                it.id = it?.id?.let(::anonymize)
                it.specimen?.id = it.specimen?.id?.let(::anonymize)
            }

            medicationRecommendations?.forEach { it ->
                it.id = it?.id?.let(::anonymize)
                it.supportingVariants?.forEach { it ->
                    it.variant?.id = it.variant?.id?.let(::anonymize)
                }
                it.reason?.id = it.reason?.id?.let(::anonymize)
            }
            reason?.id = reason?.id?.let(::anonymize)
            studyEnrollmentRecommendations?.forEach { it ->
                it?.reason?.id = it.reason?.id?.let(::anonymize)
            }

            procedureRecommendations?.forEach { it ->

                it.id = it?.id?.let(::anonymize)
                it.supportingVariants?.forEach { it ->
                    it.variant?.id = it.variant?.id?.let(::anonymize)
                }

                it.reason?.id = it.reason?.id?.let(::anonymize)

                studyEnrollmentRecommendations?.forEach { it ->

                    it.id = it?.id?.let(::anonymize)
                    it.supportingVariants.forEach { it ->
                        it.variant?.id = it?.variant?.id?.let(::anonymize)
                    }
                    responses?.forEach { it ->
                        it.id = it?.id?.let(::anonymize)
                        it.id = it?.id?.let(::anonymize)
                    }
                }
            }
        }
    }


    this.responses.forEach { it ->

        it.id = it?.id?.let(::anonymize)
        it.therapy?.id = it.therapy?.id?.let(::anonymize)

    }

    this.diagnoses.forEach { it ->

        it.id = it?.id?.let(::anonymize)
        it.histology?.forEach { it -> it.id = it?.id?.let(::anonymize) }
    }

    this.ngsReports.forEach { it ->
        it.id = it?.id?.let(::anonymize)
        it.results?.tumorCellContent?.id = it.results.tumorCellContent?.id?.let(::anonymize)
        it.results?.tumorCellContent?.specimen?.id =
            it.results?.tumorCellContent?.specimen?.id?.let(::anonymize)
        it.results?.rnaFusions?.forEach { it ->
            it?.id = it.id?.let(::anonymize)
        }
        it.results?.simpleVariants?.forEach { it ->
            it?.id = it.id?.let(::anonymize)
        }
        it.results?.tmb?.id = it.results?.tmb?.id?.let(::anonymize)
        it.results?.tmb?.specimen?.id = it.results?.tmb?.specimen?.id?.let(::anonymize)

        it.results?.brcaness?.id = it.results?.brcaness?.id?.let(::anonymize)
        it.results?.brcaness?.specimen?.id = it.results?.brcaness?.specimen?.id?.let(::anonymize)
        it.results?.copyNumberVariants?.forEach { it -> it?.id = it.id?.let(::anonymize) }
        it.results?.hrdScore?.id = it.results?.hrdScore?.id?.let(::anonymize)
        it.results?.hrdScore?.specimen?.id = it.results?.hrdScore?.specimen?.id?.let (::anonymize)
        it.results?.rnaSeqs?.forEach { it -> it?.id = it.id?.let(::anonymize) }
        it.results?.dnaFusions?.forEach { it -> it?.id = it.id?.let(::anonymize) }
        it.specimen?.id = it?.specimen?.id?.let(::anonymize)

    }

    this.histologyReports.forEach { it ->
        it.id = it?.id?.let(::anonymize)
        it.results?.tumorCellContent?.id = it.results?.tumorCellContent?.id?.let(::anonymize)
        it.results?.tumorCellContent?.specimen?.id =
            it.results?.tumorCellContent?.specimen?.id?.let(::anonymize)

        it.results?.tumorMorphology?.id = it.results?.tumorMorphology?.id?.let(::anonymize)
        it.results?.tumorMorphology?.specimen?.id =
            it.results?.tumorMorphology?.specimen?.id?.let(::anonymize)
        it.specimen?.id = it.specimen?.id?.let(::anonymize)

    }
    this.claimResponses.forEach { it ->
        it.id = it?.id?.let(::anonymize)
        it.claim?.id = it.claim?.id?.let(::anonymize)
    }
    this.claims?.forEach { it ->

        it.id = it?.id?.let(::anonymize)
        it.recommendation?.id = it.recommendation?.id?.let(::anonymize)

    }
    this.familyMemberHistories.forEach { it -> it.id = it?.id?.let(::anonymize) }
    this.guidelineProcedures.forEach { it ->
        it.id = it?.id?.let(::anonymize)
        it.reason?.id = it.reason?.id?.let(::anonymize)
        it.basedOn?.id = it.basedOn?.id?.let(::anonymize)

    }

    this.guidelineTherapies.forEach { it ->
        it.id = it?.id?.let(::anonymize)
        it.reason?.id = it.reason?.id?.let(::anonymize)
        it.basedOn?.id = it.basedOn?.id?.let(::anonymize)
    }
    this.ihcReports.forEach { it ->
        it.id = it?.id?.let(::anonymize)
        it.specimen?.id = it.specimen?.id?.let(::anonymize)
        it.results.proteinExpression.forEach { it -> it?.id = it.id.let(::anonymize) }
    }

    this.msiFindings.forEach { it ->

        it.id = it?.id?.let(::anonymize)
        it.specimen?.id = it.specimen?.id?.let(::anonymize)
    }

    this.performanceStatus.forEach { it -> it.id = it?.id?.let(::anonymize) }

    this.priorDiagnosticReports.forEach { it ->

        it.id = it?.id?.let(::anonymize)
        it.specimen?.id = it.specimen?.id?.let(::anonymize)
    }

    this.specimens.forEach { it ->

        it.id = it?.id?.let(::anonymize)
        it.diagnosis?.id = it.diagnosis?.id?.let(::anonymize)

    }

    this.systemicTherapies.forEach { it ->

        it.history?.forEach { it ->

            it.id = it?.id?.let(::anonymize)
            it.reason?.id = it.reason?.id?.let(::anonymize)
            it.basedOn?.id = it.basedOn?.id?.let(::anonymize)
        }

    }
}

fun Mtb.ensureMetaDataIsInitialized() {
    // init metadata if necessary
    if (this.metadata == null) {
        val mvhMetadata = MvhMetadata.builder().build()
        this.metadata = mvhMetadata
    }
    if (this.metadata.researchConsents == null) {
        this.metadata.researchConsents = mutableListOf()
    }
    if (this.metadata.modelProjectConsent == null) {
        this.metadata.modelProjectConsent = ModelProjectConsent()
        this.metadata.modelProjectConsent.provisions = mutableListOf()
    } else if (this.metadata.modelProjectConsent.provisions != null) {
        // make sure list can be changed
        this.metadata.modelProjectConsent.provisions =
            this.metadata.modelProjectConsent.provisions.toMutableList()
    }
}

infix fun Mtb.addGenomDeTan(pseudonymizeService: PseudonymizeService) {
    this.metadata.transferTan = pseudonymizeService.genomDeTan(PatientId(this.patient.id))
}
