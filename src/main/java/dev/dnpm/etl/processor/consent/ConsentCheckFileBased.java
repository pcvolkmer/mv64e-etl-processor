package dev.dnpm.etl.processor.consent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsentCheckFileBased implements ICheckConsent{

    private static final Logger log = LoggerFactory.getLogger(ConsentCheckFileBased.class);

    public ConsentCheckFileBased() {
        log.info("ConsentCheckFileBased initialized...");
    }

    @Override
    public TtpConsentStatus getTtpConsentStatus(String personIdentifierValue) {
        return TtpConsentStatus.UNKNOWN_CHECK_FILE;
    }
}
