package dev.dnpm.etl.processor.consent;

import java.util.Date;
import org.hl7.fhir.r4.model.Bundle;

public interface IGetConsent {

    /**
     * Get broad consent status for a patient identifier
     *
     * @param personIdentifierValue patient identifier used for consent data
     * @return status of broad consent
     * @apiNote cannot not differ between not asked and rejected
     */
    TtpConsentStatus getTtpBroadConsentStatus(String personIdentifierValue);

    /**
     * Get broad consent policies with respect to a request date
     *
     * @param personIdentifierValue patient identifier used for consent data
     * @param requestDate           target date until consent data should be considered
     * @return consent policies as bundle; <p>if empty patient has not been asked, yet.</p>
     */
    default Bundle getBroadConsent(String personIdentifierValue, Date requestDate) {
        return currentConsentForPersonAndTemplate(personIdentifierValue, ConsentDomain.BroadConsent,
            requestDate);
    }

    /**
     * Get 'GenomDe Modelvorhaben §64e' consent policies with respect to a request date
     *
     * @param personIdentifierValue patient identifier used for consent data
     * @param requestDate           target date until consent data should be considered
     * @return consent policies as bundle; <p>if empty patient has not been asked, yet.</p>
     */
    default Bundle getGenomDeConsent(String personIdentifierValue, Date requestDate) {
        return currentConsentForPersonAndTemplate(personIdentifierValue,
            ConsentDomain.Modelvorhaben64e, requestDate);
    }

    /**
     * Get consent policies with respect to a request date
     *
     * @param personIdentifierValue patient identifier used for consent data
     * @param targetConsentDomain   domain which should be used to request consent
     * @param requestDate           target date until consent data should be considered
     * @return consent policies as bundle; <p>if empty patient has not been asked, yet.</p>
     */
    Bundle currentConsentForPersonAndTemplate(String personIdentifierValue,
        ConsentDomain targetConsentDomain, Date requestDate);

}
