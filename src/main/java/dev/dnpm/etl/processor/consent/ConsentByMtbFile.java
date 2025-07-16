package dev.dnpm.etl.processor.consent;

import java.util.Date;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsentByMtbFile implements IGetConsent {

    private static final Logger log = LoggerFactory.getLogger(ConsentByMtbFile.class);

    public ConsentByMtbFile() {
        log.info("ConsentCheckFileBased initialized...");
    }

    @Override
    public TtpConsentStatus getTtpBroadConsentStatus(String personIdentifierValue) {
        return TtpConsentStatus.UNKNOWN_CHECK_FILE;
    }

    @Override
    public Bundle getBroadConsent(String personIdentifierValue, Date requestDate) {
        return IGetConsent.super.getBroadConsent(personIdentifierValue, requestDate);
    }

    @Override
    public Bundle getGenomDeConsent(String personIdentifierValue, Date requestDate) {
        return IGetConsent.super.getGenomDeConsent(personIdentifierValue, requestDate);
    }

    @Override
    public Bundle currentConsentForPersonAndTemplate(String personIdentifierValue,
        ConsentDomain targetConsentDomain, Date requestDate) {
        return new Bundle();
    }
}
