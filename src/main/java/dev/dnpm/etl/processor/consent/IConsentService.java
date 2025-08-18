package dev.dnpm.etl.processor.consent;

import java.util.Date;
import org.hl7.fhir.r4.model.Bundle;

public interface IConsentService {

    /**
     * Get broad consent status for a patient identifier
     *
     * @param personIdentifierValue patient identifier used for consent data
     * @return status of broad consent
     * @apiNote cannot not differ between not asked and rejected
     *
     */
    TtpConsentStatus getTtpBroadConsentStatus(String personIdentifierValue);

    /**
     * Get broad consent policies with respect to a request date
     *
     * @param personIdentifierValue patient identifier used for consent data
     * @param requestDate           target date until consent data should be considered
     * @return consent policies as bundle; <p>if empty patient has not been asked, yet.</p>
     */
    Bundle getConsent(String personIdentifierValue, Date requestDate, ConsentDomain consentDomain);

}
