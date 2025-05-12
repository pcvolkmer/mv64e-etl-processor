package dev.dnpm.etl.processor.consent;

public class ConsentCheckFileBased implements ICheckConsent{

    @Override
    public TtpConsentStatus getTtpConsentStatus(String personIdentifierValue) {
        return TtpConsentStatus.UNKNOWN_CHECK_FILE;
    }
}
