package dev.dnpm.etl.processor

class Fingerprint(val value: String) {
    override fun hashCode() = value.hashCode()

    override fun equals(other: Any?) = other is Fingerprint && other.value == value

    companion object {
        fun empty() = Fingerprint("")
    }
}
