package dev.dnpm.etl.processor.consent;

import dev.pcvolkmer.mv64e.mtb.Mtb;
import java.util.Date;
import org.apache.commons.lang3.NotImplementedException;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Consent.ConsentProvisionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsentCheckFileBased implements ICheckConsent {

    private static final Logger log = LoggerFactory.getLogger(ConsentCheckFileBased.class);

    public ConsentCheckFileBased() {
        log.info("ConsentCheckFileBased initialized...");
    }

    @Override
    public TtpConsentStatus getTtpBroadConsentStatus(String personIdentifierValue) {
        return TtpConsentStatus.UNKNOWN_CHECK_FILE;
    }

    @Override
    public Bundle getBroadConsent(String personIdentifierValue, Date requestDate) {
        return ICheckConsent.super.getBroadConsent(personIdentifierValue, requestDate);
    }

    @Override
    public Bundle getGenomDeConsent(String personIdentifierValue, Date requestDate) {
        return ICheckConsent.super.getGenomDeConsent(personIdentifierValue, requestDate);
    }

    @Override
    public Bundle currentConsentForPersonAndTemplate(String personIdentifierValue,
        ConsentDomain targetConsentDomain, Date requestDate) {
        return new Bundle();
    }

    @Override
    public ConsentProvisionType getProvisionTypeByPolicyCode(Bundle consentBundle,
        Date requestDate, ConsentDomain consentDomain) {
        return ConsentProvisionType.NULL;
    }
}
