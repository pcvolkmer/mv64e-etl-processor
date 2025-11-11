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

package dev.dnpm.etl.processor.consent

import dev.pcvolkmer.mv64e.mtb.ConsentProvision
import dev.pcvolkmer.mv64e.mtb.ModelProjectConsentPurpose
import dev.pcvolkmer.mv64e.mtb.Mtb
import org.springframework.stereotype.Service

/** Evaluates consent using provided consent service and file based consent information */
@Service
class ConsentEvaluator(
    private val consentService: IConsentService,
) {
    fun check(mtbFile: Mtb): ConsentEvaluation {
        val ttpConsentStatus = consentService.getTtpBroadConsentStatus(mtbFile.patient.id)
        val consentGiven =
            ttpConsentStatus == TtpConsentStatus.BROAD_CONSENT_GIVEN ||
                ttpConsentStatus == TtpConsentStatus.GENOM_DE_CONSENT_SEQUENCING_PERMIT ||
                // Aktuell nur Modellvorhaben Consent im File
                ttpConsentStatus == TtpConsentStatus.UNKNOWN_CHECK_FILE &&
                mtbFile.metadata?.modelProjectConsent?.provisions?.any {
                    it.purpose == ModelProjectConsentPurpose.SEQUENCING &&
                        it.type == ConsentProvision.PERMIT
                } == true

        return ConsentEvaluation(ttpConsentStatus, consentGiven)
    }
}

data class ConsentEvaluation(
    private val ttpConsentStatus: TtpConsentStatus,
    private val consentGiven: Boolean,
) {
    /** Checks if any required consent is present */
    fun hasConsent(): Boolean = consentGiven

    /** Returns the consent status */
    fun getStatus(): TtpConsentStatus {
        if (ttpConsentStatus == TtpConsentStatus.UNKNOWN_CHECK_FILE) {
            // in case ttp check is disabled - we propagate rejected status anyway
            return TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED
        }
        return ttpConsentStatus
    }
}
