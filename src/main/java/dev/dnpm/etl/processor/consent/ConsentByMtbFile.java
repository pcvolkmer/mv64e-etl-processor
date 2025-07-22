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

    /**
     * EMPTY METHOD: NOT IMPLEMENTED
     *
     * @return empty bundle
     */
    @Override
    public Bundle getConsent(String personIdentifierValue, Date requestDate,
        ConsentDomain consentDomain) {
        return new Bundle();
    }
}
