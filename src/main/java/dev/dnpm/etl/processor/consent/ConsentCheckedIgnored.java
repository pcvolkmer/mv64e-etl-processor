package dev.dnpm.etl.processor.consent;

public class ConsentCheckedIgnored implements ICheckConsent{

    @Override
    public ConsentStatus isConsented(String personIdentifierValue) {
        return ConsentStatus.IGNORED;
    }
}
