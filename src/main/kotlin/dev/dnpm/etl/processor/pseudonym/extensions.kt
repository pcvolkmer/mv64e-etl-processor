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

/**
 * Replaces patient ID with generated patient pseudonym
 *
 * @param pseudonymizeService The pseudonymizeService to be used
 * @return The MTB file containing patient pseudonymes
 * @since 0.11.0
 */
infix fun Mtb.pseudonymizeWith(pseudonymizeService: PseudonymizeService) {
  val patientPseudonym = pseudonymizeService.patientPseudonym(PatientId(this.patient.id)).value

  this.episodesOfCare?.filterNotNull()?.forEach { it.patient?.id = patientPseudonym }
  this.carePlans?.filterNotNull()?.forEach { carePlan ->
    carePlan.patient.id = patientPseudonym
    carePlan.rebiopsyRequests?.filterNotNull()?.forEach { it.patient?.id = patientPseudonym }
    carePlan.histologyReevaluationRequests?.filterNotNull()?.forEach {
      it.patient?.id = patientPseudonym
    }
    carePlan.medicationRecommendations?.filterNotNull()?.forEach {
      it.patient?.id = patientPseudonym
    }
    carePlan.studyEnrollmentRecommendations?.filterNotNull()?.forEach {
      it.patient?.id = patientPseudonym
    }
    carePlan.procedureRecommendations?.filterNotNull()?.forEach {
      it.patient?.id = patientPseudonym
    }
    carePlan.geneticCounselingRecommendation?.patient?.id = patientPseudonym
  }
  this.diagnoses?.filterNotNull()?.forEach { it.patient?.id = patientPseudonym }
  this.guidelineTherapies?.filterNotNull()?.forEach { it.patient?.id = patientPseudonym }
  this.guidelineProcedures?.filterNotNull()?.forEach { it.patient?.id = patientPseudonym }
  this.patient.id = patientPseudonym
  this.claims?.filterNotNull()?.forEach { it.patient?.id = patientPseudonym }
  this.claimResponses?.filterNotNull()?.forEach { it.patient?.id = patientPseudonym }
  this.familyMemberHistories?.filterNotNull()?.forEach { it.patient?.id = patientPseudonym }
  this.histologyReports?.filterNotNull()?.forEach {
    it.patient.id = patientPseudonym
    it.results.tumorMorphology?.patient?.id = patientPseudonym
    it.results.tumorCellContent?.patient?.id = patientPseudonym
  }
  this.ngsReports?.filterNotNull()?.forEach { ngsReport ->
    ngsReport.patient?.id = patientPseudonym
    ngsReport.results?.simpleVariants?.filterNotNull()?.forEach {
      it.patient?.id = patientPseudonym
    }
    ngsReport.results?.copyNumberVariants?.filterNotNull()?.forEach {
      it.patient?.id = patientPseudonym
    }
    ngsReport.results?.dnaFusions?.filterNotNull()?.forEach { it.patient?.id = patientPseudonym }
    ngsReport.results?.rnaFusions?.filterNotNull()?.forEach { it.patient?.id = patientPseudonym }
    ngsReport.results?.tumorCellContent?.patient?.id = patientPseudonym
    ngsReport.results?.brcaness?.patient?.id = patientPseudonym
    ngsReport.results?.tmb?.patient?.id = patientPseudonym
    ngsReport.results?.hrdScore?.patient?.id = patientPseudonym
  }
  this.ihcReports?.filterNotNull()?.forEach { ihcReports ->
    ihcReports.patient?.id = patientPseudonym
    ihcReports.results?.msiMmr?.filterNotNull()?.forEach { it.patient?.id = patientPseudonym }
    ihcReports.results?.proteinExpression?.filterNotNull()?.forEach {
      it.patient?.id = patientPseudonym
    }
  }
  this.responses?.filterNotNull()?.forEach { it.patient?.id = patientPseudonym }
  this.specimens?.filterNotNull()?.forEach { it.patient?.id = patientPseudonym }
  this.priorDiagnosticReports?.filterNotNull()?.forEach { it.patient?.id = patientPseudonym }
  this.performanceStatus?.filterNotNull()?.forEach { it.patient?.id = patientPseudonym }
  this.systemicTherapies?.filterNotNull()?.forEach { systemicTherapy ->
    systemicTherapy.history?.filterNotNull()?.forEach { it.patient?.id = patientPseudonym }
  }
  this.followUps?.filterNotNull()?.forEach { it.patient?.id = patientPseudonym }

  this.msiFindings?.filterNotNull()?.forEach { it.patient?.id = patientPseudonym }

  this.metadata?.researchConsents?.filterNotNull()?.forEach { researchConsent ->
    if (researchConsent.contains("patient")) {
      // here we expect only a patient reference any other data like display
      // need to be removed, since may contain unsecure data
      researchConsent.remove("patient")
      researchConsent["patient"] = mapOf("reference" to "Patient/$patientPseudonym")
    }
  }
}

/**
 * Creates new hash of content IDs with given prefix except for patient IDs
 *
 * @param pseudonymizeService The pseudonymizeService to be used
 * @return The MTB file containing rehashed content IDs
 * @since 0.11.0
 */
infix fun Mtb.anonymizeContentWith(pseudonymizeService: PseudonymizeService) {
  val prefix = pseudonymizeService.prefix()

  fun anonymize(id: String): String {
    val hash = DigestUtils.sha256Hex("$prefix-$id").substring(0, 41).lowercase()
    return "$prefix$hash"
  }

  this.episodesOfCare?.filterNotNull()?.forEach { episodeOfCare ->
    episodeOfCare.apply { id = id?.let(::anonymize) }
    episodeOfCare.diagnoses?.filterNotNull()?.forEach { it.id = it.id?.let(::anonymize) }
  }

  this.carePlans?.onEach { carePlan ->
    carePlan?.apply {
      this.id = id?.let { anonymize(it) }

      this.geneticCounselingRecommendation?.apply { this.id = this.id?.let(::anonymize) }
      this.rebiopsyRequests?.filterNotNull()?.forEach { rebiopsyRequest ->
        rebiopsyRequest.id = rebiopsyRequest.id?.let(::anonymize)
        rebiopsyRequest.tumorEntity?.id = rebiopsyRequest.tumorEntity?.id?.let(::anonymize)
      }
      this.histologyReevaluationRequests?.filterNotNull()?.forEach { histologyReevaluationRequest ->
        histologyReevaluationRequest.id = histologyReevaluationRequest.id?.let(::anonymize)
        histologyReevaluationRequest.specimen?.id =
            histologyReevaluationRequest.specimen?.id?.let(::anonymize)
      }

      this.medicationRecommendations?.filterNotNull()?.forEach { medicationRecommendations ->
        medicationRecommendations.id = medicationRecommendations.id?.let(::anonymize)
        medicationRecommendations.supportingVariants?.filterNotNull()?.forEach {
          it.variant?.id = it.variant?.id?.let(::anonymize)
        }
        medicationRecommendations.reason?.id =
            medicationRecommendations.reason?.id?.let(::anonymize)
      }
      this.reason?.id = this.reason?.id?.let(::anonymize)
      this.studyEnrollmentRecommendations?.filterNotNull()?.forEach { studyEnrollmentRecommendation
        ->
        studyEnrollmentRecommendation.reason?.id =
            studyEnrollmentRecommendation.reason?.id?.let(::anonymize)
      }
      this.procedureRecommendations?.filterNotNull()?.forEach { procedureRecommendation ->
        procedureRecommendation.id = procedureRecommendation.id?.let(::anonymize)
        procedureRecommendation.supportingVariants?.filterNotNull()?.forEach {
          it.variant?.id = it.variant?.id?.let(::anonymize)
        }
        procedureRecommendation.reason?.id = procedureRecommendation.reason?.id?.let(::anonymize)
      }
      this.studyEnrollmentRecommendations?.filterNotNull()?.forEach { studyEnrollmentRecommendation
        ->
        studyEnrollmentRecommendation.id = studyEnrollmentRecommendation.id?.let(::anonymize)
        studyEnrollmentRecommendation.supportingVariants.forEach {
          it.variant?.id = it?.variant?.id?.let(::anonymize)
        }
      }
    }
  }

  this.responses?.filterNotNull()?.forEach { response ->
    response.id = response.id?.let(::anonymize)
    response.therapy?.id = response.therapy?.id?.let(::anonymize)
  }

  this.diagnoses?.filterNotNull()?.forEach { diagnose ->
    diagnose.id = diagnose.id?.let(::anonymize)
    diagnose.histology?.filterNotNull()?.forEach { it.id = it.id?.let(::anonymize) }
  }

  this.ngsReports?.filterNotNull()?.forEach { ngsReport ->
    ngsReport.id = ngsReport.id?.let(::anonymize)
    ngsReport.results?.tumorCellContent?.id =
        ngsReport.results.tumorCellContent?.id?.let(::anonymize)
    ngsReport.results?.tumorCellContent?.specimen?.id =
        ngsReport.results?.tumorCellContent?.specimen?.id?.let(::anonymize)
    ngsReport.results?.rnaFusions?.filterNotNull()?.forEach { it.id = it.id?.let(::anonymize) }
    ngsReport.results?.simpleVariants?.filterNotNull()?.forEach {
      it.id = it.id?.let(::anonymize)
      it.transcriptId?.value = it.transcriptId?.value?.let(::anonymize)
    }
    ngsReport.results?.tmb?.id = ngsReport.results?.tmb?.id?.let(::anonymize)
    ngsReport.results?.tmb?.specimen?.id = ngsReport.results?.tmb?.specimen?.id?.let(::anonymize)

    ngsReport.results?.brcaness?.id = ngsReport.results?.brcaness?.id?.let(::anonymize)
    ngsReport.results?.brcaness?.specimen?.id =
        ngsReport.results?.brcaness?.specimen?.id?.let(::anonymize)
    ngsReport.results?.copyNumberVariants?.filterNotNull()?.forEach {
      it.id = it.id?.let(::anonymize)
    }
    ngsReport.results?.hrdScore?.id = ngsReport.results?.hrdScore?.id?.let(::anonymize)
    ngsReport.results?.hrdScore?.specimen?.id =
        ngsReport.results?.hrdScore?.specimen?.id?.let(::anonymize)
    ngsReport.results?.rnaSeqs?.filterNotNull()?.forEach { it.id = it.id?.let(::anonymize) }
    ngsReport.results?.dnaFusions?.filterNotNull()?.forEach { it.id = it.id?.let(::anonymize) }
    ngsReport.specimen?.id = ngsReport.specimen?.id?.let(::anonymize)
  }

  this.histologyReports?.filterNotNull()?.forEach { histologyReport ->
    histologyReport.id = histologyReport.id?.let(::anonymize)
    histologyReport.results?.tumorCellContent?.id =
        histologyReport.results?.tumorCellContent?.id?.let(::anonymize)
    histologyReport.results?.tumorCellContent?.specimen?.id =
        histologyReport.results?.tumorCellContent?.specimen?.id?.let(::anonymize)

    histologyReport.results?.tumorMorphology?.id =
        histologyReport.results?.tumorMorphology?.id?.let(::anonymize)
    histologyReport.results?.tumorMorphology?.specimen?.id =
        histologyReport.results?.tumorMorphology?.specimen?.id?.let(::anonymize)
    histologyReport.specimen?.id = histologyReport.specimen?.id?.let(::anonymize)
  }
  this.claimResponses?.filterNotNull()?.forEach { claimResponse ->
    claimResponse.id = claimResponse.id?.let(::anonymize)
    claimResponse.claim?.id = claimResponse.claim?.id?.let(::anonymize)
  }
  this.claims?.filterNotNull()?.forEach { claim ->
    claim.id = claim.id?.let(::anonymize)
    claim.recommendation?.id = claim.recommendation?.id?.let(::anonymize)
  }
  this.familyMemberHistories?.filterNotNull()?.forEach { it.id = it.id?.let(::anonymize) }
  this.guidelineProcedures?.filterNotNull()?.forEach { guidelineProcedure ->
    guidelineProcedure.id = guidelineProcedure.id?.let(::anonymize)
    guidelineProcedure.reason?.id = guidelineProcedure.reason?.id?.let(::anonymize)
    guidelineProcedure.basedOn?.id = guidelineProcedure.basedOn?.id?.let(::anonymize)
  }

  this.guidelineTherapies?.filterNotNull()?.forEach { guidelineTherapy ->
    guidelineTherapy.id = guidelineTherapy.id?.let(::anonymize)
    guidelineTherapy.reason?.id = guidelineTherapy.reason?.id?.let(::anonymize)
    guidelineTherapy.basedOn?.id = guidelineTherapy.basedOn?.id?.let(::anonymize)
  }
  this.ihcReports?.filterNotNull()?.forEach { ihcReport ->
    ihcReport.id = ihcReport.id?.let(::anonymize)
    ihcReport.specimen?.id = ihcReport.specimen?.id?.let(::anonymize)
    ihcReport.results?.proteinExpression?.filterNotNull()?.forEach {
      it.id = it.id.let(::anonymize)
    }
  }

  this.msiFindings?.filterNotNull()?.forEach { msiFinding ->
    msiFinding.id = msiFinding.id?.let(::anonymize)
    msiFinding.specimen?.id = msiFinding.specimen?.id?.let(::anonymize)
  }

  this.performanceStatus?.filterNotNull()?.forEach { it.id = it.id?.let(::anonymize) }

  this.priorDiagnosticReports?.filterNotNull()?.forEach { priorDiagnosticReport ->
    priorDiagnosticReport.id = priorDiagnosticReport.id?.let(::anonymize)
    priorDiagnosticReport.specimen?.id = priorDiagnosticReport.specimen?.id?.let(::anonymize)
  }

  this.specimens?.filterNotNull()?.forEach { specimen ->
    specimen.id = specimen.id?.let(::anonymize)
    specimen.diagnosis?.id = specimen.diagnosis?.id?.let(::anonymize)
  }

  this.systemicTherapies?.filterNotNull()?.forEach { systemicTherapy ->
    systemicTherapy.history?.filterNotNull()?.forEach { history ->
      history.id = history.id?.let(::anonymize)
      history.reason?.id = history.reason?.id?.let(::anonymize)
      history.basedOn?.id = history.basedOn?.id?.let(::anonymize)
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
  this.metadata?.transferTan = pseudonymizeService.genomDeTan(PatientId(this.patient.id))
}
