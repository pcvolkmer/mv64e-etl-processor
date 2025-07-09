package dev.dnpm.etl.processor.consent;

public enum TtpConsentStatus {
    /**
     * Valid consent found
     */
    BROAD_CONSENT_GIVEN,
    /**
     * Missing or rejected...actually unknown
     */
    BROAD_CONSENT_MISSING_OR_REJECTED,
    /**
     * No Broad consent policy found
     */
    BROAD_CONSENT_MISSING,
    /**
     * Research policy has been rejected
     */
    BROAD_CONSENT_REJECTED,

    GENOM_DE_CONSENT_SEQUENCING_PERMIT,
    /**
     * No GenomDE consent policy found
     */
    GENOM_DE_CONSENT_MISSING,
    /**
     * GenomDE consent policy found, but has been rejected
     */
    GENOM_DE_SEQUENCING_REJECTED,
    /**
     * Consent status is validate via file property 'consent.status'
     */
    UNKNOWN_CHECK_FILE,
    /**
     * Due technical problems consent status is unknown
     */
    FAILED_TO_ASK
}
