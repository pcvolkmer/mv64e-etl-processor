package dev.dnpm.etl.processor.consent;


public interface ICheckConsent {

    ConsentStatus isConsented(String personIdentifierValue);

}
