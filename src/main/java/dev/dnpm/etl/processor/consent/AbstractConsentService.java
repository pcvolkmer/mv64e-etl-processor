/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2023       Comprehensive Cancer Center Mainfranken
 * Copyright (c) 2023-2025  Paul-Christian Volkmer, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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

package dev.dnpm.etl.processor.consent;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class AbstractConsentService implements IConsentService {

  protected final Logger log;
  protected final FhirContext fhirContext;

  protected AbstractConsentService(FhirContext fhirContext, Logger log) {
    this.fhirContext = fhirContext;
    this.log = log;
  }

  protected TtpConsentStatus evaluateConsentResponse(@Nullable String consentStatusResponse) {
    if (consentStatusResponse == null) {
      return TtpConsentStatus.FAILED_TO_ASK;
    }
    try {
      var response = fhirContext.newJsonParser().parseResource(consentStatusResponse);

      if (response instanceof Parameters responseParameters) {

        var responseValue = responseParameters.getParameter("consented").getValue();
        var isConsented = responseValue.castToBoolean(responseValue);
        if (!isConsented.hasValue()) {
          return TtpConsentStatus.FAILED_TO_ASK;
        }
        if (isConsented.booleanValue()) {
          return TtpConsentStatus.BROAD_CONSENT_GIVEN;
        } else {
          return TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED;
        }
      } else if (response instanceof OperationOutcome outcome) {
        log.error(
            "failed to get consent status from ttp. probably configuration error. "
                + "outcome: '{}'",
            fhirContext.newJsonParser().encodeToString(outcome));
      }
    } catch (DataFormatException dfe) {
      log.error("failed to parse response to FHIR R4 resource.", dfe);
    }
    return TtpConsentStatus.FAILED_TO_ASK;
  }
}
