package dev.dnpm.etl.processor.consent;

public enum ConsentStatus {
    CONSENTED,
    CONSENT_MISSING,
    FAILED_TO_ASK,
    IGNORED, CONSENT_REJECTED
}
