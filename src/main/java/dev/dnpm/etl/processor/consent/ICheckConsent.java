package dev.dnpm.etl.processor.consent;


public interface ICheckConsent {

    TtpConsentStatus getTtpConsentStatus(String personIdentifierValue);

}
