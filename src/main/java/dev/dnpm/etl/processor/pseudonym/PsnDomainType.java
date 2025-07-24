package dev.dnpm.etl.processor.pseudonym;

public enum PsnDomainType {
    /**
     * one pseudonym per original value
     */
    SINGLE_PSN_DOMAIN,
    /**
     * multiple pseudonymes for one original value
     */
    MULTI_PSN_DOMAIN
}
