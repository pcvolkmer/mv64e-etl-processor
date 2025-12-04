package dev.dnpm.etl.processor.consent;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Consent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates MII Broad Consent
 *
 * @since 0.12
 */
@NullMarked
public class MiiBroadConsentEvaluator {

  private static final Logger log = LoggerFactory.getLogger(MiiBroadConsentEvaluator.class);

  /**
   * Evaluates MII Broad Consent
   *
   * @param fhirContext FHIR context
   * @param consentStatusResponse Nullable String containing FHIR String
   * @return consent status
   */
  public static TtpConsentStatus evaluate(
      FhirContext fhirContext, @Nullable String consentStatusResponse) {
    if (null == consentStatusResponse) {
      return TtpConsentStatus.FAILED_TO_ASK;
    }
    try {
      var response = fhirContext.newJsonParser().parseResource(consentStatusResponse);

      if (response instanceof Bundle bundle) {
        Boolean mdatStoreAndProcessGiven = null;
        Boolean mdatResearchUse = null;
        Boolean patdatStoreAndUse = null;
        for (var entry : bundle.getEntry()) {
          if (entry.getResource() instanceof Consent consent) {
            for (var provision : consent.getProvision().getProvision()) {
              for (var code : provision.getCode()) {
                for (var coding : code.getCoding()) {
                  if ("2.16.840.1.113883.3.1937.777.24.5.3.7".equals(coding.getCode())) {
                    mdatStoreAndProcessGiven =
                        Consent.ConsentProvisionType.PERMIT.equals(provision.getType());
                  } else if ("2.16.840.1.113883.3.1937.777.24.5.3.8".equals(coding.getCode())) {
                    mdatResearchUse =
                        Consent.ConsentProvisionType.PERMIT.equals(provision.getType());
                  } else if ("2.16.840.1.113883.3.1937.777.24.5.3.1".equals(coding.getCode())) {
                    patdatStoreAndUse =
                        Consent.ConsentProvisionType.PERMIT.equals(provision.getType());
                  }
                }
              }
            }
          }
        }
        if (null != mdatStoreAndProcessGiven
            && null != mdatResearchUse
            && mdatStoreAndProcessGiven
            && mdatResearchUse) {
          return TtpConsentStatus.BROAD_CONSENT_GIVEN;
        }

        if (null != patdatStoreAndUse && patdatStoreAndUse) {
          return TtpConsentStatus.BROAD_CONSENT_GIVEN;
        }
        return TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED;
      }
    } catch (DataFormatException dfe) {
      log.error("failed to parse response to FHIR R4 resource.", dfe);
    }
    return TtpConsentStatus.FAILED_TO_ASK;
  }
}
