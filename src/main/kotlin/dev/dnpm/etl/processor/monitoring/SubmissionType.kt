package dev.dnpm.etl.processor.monitoring

enum class SubmissionType(
    val value: String,
) {
    UNKNOWN("unknown"),
    INITIAL("initial"),
    ADDITION("addition"),
    CORRECTION("correction"),
    FOLLOWUP("followup"),
    TEST("test"),
}
