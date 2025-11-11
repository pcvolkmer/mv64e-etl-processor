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

package dev.dnpm.etl.processor.services

import com.fasterxml.jackson.databind.ObjectMapper
import dev.dnpm.etl.processor.*
import dev.dnpm.etl.processor.config.AppConfigProperties
import dev.dnpm.etl.processor.consent.TtpConsentStatus
import dev.dnpm.etl.processor.monitoring.Report
import dev.dnpm.etl.processor.monitoring.Request
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.dnpm.etl.processor.monitoring.RequestType
import dev.dnpm.etl.processor.output.DeleteRequest
import dev.dnpm.etl.processor.output.DnpmV2MtbFileRequest
import dev.dnpm.etl.processor.output.MtbFileRequest
import dev.dnpm.etl.processor.output.MtbFileSender
import dev.dnpm.etl.processor.pseudonym.PseudonymizeService
import dev.dnpm.etl.processor.pseudonym.addGenomDeTan
import dev.dnpm.etl.processor.pseudonym.anonymizeContentWith
import dev.dnpm.etl.processor.pseudonym.pseudonymizeWith
import dev.pcvolkmer.mv64e.mtb.ConsentProvision
import dev.pcvolkmer.mv64e.mtb.ModelProjectConsentPurpose
import dev.pcvolkmer.mv64e.mtb.Mtb
import java.time.Instant
import java.util.*
import org.apache.commons.codec.binary.Base32
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class RequestProcessor(
    private val pseudonymizeService: PseudonymizeService,
    private val transformationService: TransformationService,
    private val sender: MtbFileSender,
    private val requestService: RequestService,
    private val objectMapper: ObjectMapper,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val appConfigProperties: AppConfigProperties,
    private val consentProcessor: ConsentProcessor?,
) {

  private var logger: Logger = LoggerFactory.getLogger("RequestProcessor")

  fun processMtbFile(mtbFile: Mtb) {
    processMtbFile(mtbFile, randomRequestId())
  }

  fun processMtbFile(mtbFile: Mtb, requestId: RequestId) {
    val pid = PatientId(extractPatientIdentifier(mtbFile))

    val isConsentOk =
        consentProcessor != null && consentProcessor.consentGatedCheckAndTryEmbedding(mtbFile) ||
            consentProcessor == null
    if (isConsentOk) {
      if (isGenomDeConsented(mtbFile)) {
        mtbFile addGenomDeTan pseudonymizeService
      }
      mtbFile pseudonymizeWith pseudonymizeService
      mtbFile anonymizeContentWith pseudonymizeService
      val request = DnpmV2MtbFileRequest(requestId, transformationService.transform(mtbFile))
      saveAndSend(request, pid)
    } else {
      logger.warn("consent check failed file will not be processed further!")
      applicationEventPublisher.publishEvent(
          ResponseEvent(requestId, Instant.now(), RequestStatus.NO_CONSENT)
      )
    }
  }

  private fun isGenomDeConsented(mtbFile: Mtb): Boolean {
    val isModelProjectConsented =
        mtbFile.metadata?.modelProjectConsent?.provisions?.any { p ->
          p.purpose == ModelProjectConsentPurpose.SEQUENCING && p.type == ConsentProvision.PERMIT
        } == true
    return isModelProjectConsented
  }

  private fun <T> saveAndSend(request: MtbFileRequest<T>, pid: PatientId) {
    requestService.save(
        Request(
            request.requestId,
            request.patientPseudonym(),
            pid,
            fingerprint(request),
            RequestType.MTB_FILE,
            RequestStatus.UNKNOWN,
        )
    )

    if (appConfigProperties.duplicationDetection && isDuplication(request)) {
      applicationEventPublisher.publishEvent(
          ResponseEvent(request.requestId, Instant.now(), RequestStatus.DUPLICATION)
      )
      return
    }

    val responseStatus = sender.send(request)

    applicationEventPublisher.publishEvent(
        ResponseEvent(
            request.requestId,
            Instant.now(),
            responseStatus.status,
            when (responseStatus.status) {
              RequestStatus.ERROR,
              RequestStatus.WARNING -> Optional.of(responseStatus.body)
              else -> Optional.empty()
            },
        )
    )
  }

  private fun <T> isDuplication(pseudonymizedMtbFileRequest: MtbFileRequest<T>): Boolean {
    val patientPseudonym =
        when (pseudonymizedMtbFileRequest) {
          is DnpmV2MtbFileRequest ->
              PatientPseudonym(pseudonymizedMtbFileRequest.content.patient.id)
        }

    val lastMtbFileRequestForPatient =
        requestService.lastMtbFileRequestForPatientPseudonym(patientPseudonym)
    val isLastRequestDeletion =
        requestService.isLastRequestWithKnownStatusDeletion(patientPseudonym)

    return null != lastMtbFileRequestForPatient &&
        !isLastRequestDeletion &&
        lastMtbFileRequestForPatient.fingerprint == fingerprint(pseudonymizedMtbFileRequest)
  }

  fun processDeletion(patientId: PatientId, isConsented: TtpConsentStatus) {
    processDeletion(patientId, randomRequestId(), isConsented)
  }

  fun processDeletion(patientId: PatientId, requestId: RequestId, isConsented: TtpConsentStatus) {
    try {
      val patientPseudonym = pseudonymizeService.patientPseudonym(patientId)

      val requestStatus: RequestStatus =
          when (isConsented) {
            TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED,
            TtpConsentStatus.BROAD_CONSENT_MISSING,
            TtpConsentStatus.BROAD_CONSENT_REJECTED -> RequestStatus.NO_CONSENT
            TtpConsentStatus.FAILED_TO_ASK -> RequestStatus.ERROR
            TtpConsentStatus.BROAD_CONSENT_GIVEN,
            TtpConsentStatus.UNKNOWN_CHECK_FILE -> RequestStatus.UNKNOWN
            TtpConsentStatus.GENOM_DE_CONSENT_SEQUENCING_PERMIT,
            TtpConsentStatus.GENOM_DE_CONSENT_MISSING,
            TtpConsentStatus.GENOM_DE_SEQUENCING_REJECTED -> {
              throw RuntimeException(
                  "processDelete should never deal with '" +
                      isConsented.name +
                      "' consent status. This is a bug and need to be fixed!"
              )
            }
          }

      requestService.save(
          Request(
              requestId,
              patientPseudonym,
              patientId,
              fingerprint(patientPseudonym.value),
              RequestType.DELETE,
              requestStatus,
          )
      )

      val responseStatus = sender.send(DeleteRequest(requestId, patientPseudonym))

      applicationEventPublisher.publishEvent(
          ResponseEvent(
              requestId,
              Instant.now(),
              responseStatus.status,
              when (responseStatus.status) {
                RequestStatus.WARNING,
                RequestStatus.ERROR -> Optional.of(responseStatus.body)
                else -> Optional.empty()
              },
          )
      )
    } catch (_: Exception) {
      requestService.save(
          Request(
              uuid = requestId,
              patientPseudonym = emptyPatientPseudonym(),
              pid = patientId,
              fingerprint = Fingerprint.empty(),
              status = RequestStatus.ERROR,
              type = RequestType.DELETE,
              report = Report("Fehler bei der Pseudonymisierung"),
          )
      )
    }
  }

  private fun <T> fingerprint(request: MtbFileRequest<T>): Fingerprint {
    return when (request) {
      is DnpmV2MtbFileRequest -> fingerprint(objectMapper.writeValueAsString(request.content))
    }
  }

  private fun fingerprint(s: String): Fingerprint {
    return Fingerprint(
        Base32()
            .encodeAsString(DigestUtils.sha256(s))
            .replace("=", "")
            .lowercase()
    )
  }
}

private fun extractPatientIdentifier(mtbFile: Mtb): String = mtbFile.patient.id
