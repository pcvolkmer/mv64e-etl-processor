package dev.dnpm.etl.processor.config

import dev.dnpm.etl.processor.Fingerprint
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration

@Configuration
class AppJdbcConfiguration : AbstractJdbcConfiguration() {
    override fun userConverters(): MutableList<*> = mutableListOf(StringToFingerprintConverter(), FingerprintToStringConverter())
}

class StringToFingerprintConverter : Converter<String, Fingerprint> {
    override fun convert(source: String): Fingerprint = Fingerprint(source)
}

class FingerprintToStringConverter : Converter<Fingerprint, String> {
    override fun convert(source: Fingerprint): String = source.value
}
