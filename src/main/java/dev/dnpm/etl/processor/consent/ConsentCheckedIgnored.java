package dev.dnpm.etl.processor.consent;

public class ConsentCheckedIgnored implements ICheckConsent{

    @Override
    public TtpConsentStatus isConsented(String personIdentifierValue) {
        return TtpConsentStatus.IGNORED;
    }
}
