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
