package dev.dnpm.etl.processor.consent;


public interface ICheckConsent {

    TtpConsentStatus isConsented(String personIdentifierValue);

}
