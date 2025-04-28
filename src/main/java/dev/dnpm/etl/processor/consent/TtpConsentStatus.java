package dev.dnpm.etl.processor.consent;

public enum TtpConsentStatus {
    /**
     * Valid consent found
     */
    CONSENTED,

    CONSENT_MISSING_OR_REJECTED,
    /**
     * Due technical problems consent status is unknown
     */
    FAILED_TO_ASK,
    /**
     * We assume received files are consented
     */
    IGNORED
}
