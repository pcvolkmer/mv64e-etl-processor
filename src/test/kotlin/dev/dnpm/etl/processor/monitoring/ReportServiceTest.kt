package dev.dnpm.etl.processor.monitoring

import dev.dnpm.etl.processor.config.JacksonConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class ReportServiceTest {

    lateinit var service: ReportService

    @BeforeEach
    fun setUp() {
        val jacksonConfig = JacksonConfig()
        service = ReportService(jacksonConfig.objectMapper())
    }

    @Test
    fun shouldParseDataQualityReport() {
        val dataQualityReport = Objects.requireNonNull(this.javaClass.classLoader.getResource("dip-response.json"))
            .readText()

        val actual = service.deserialize(dataQualityReport)

        assertThat(actual).isNotNull
        assertThat(actual).hasSize(6)
        assertThat(actual[0].severity).isEqualTo(ReportService.Severity.FATAL)
        assertThat(actual[1].severity).isEqualTo(ReportService.Severity.ERROR)
        assertThat(actual[2].severity).isEqualTo(ReportService.Severity.WARNING)
        assertThat(actual[3].severity).isEqualTo(ReportService.Severity.WARNING)
        assertThat(actual[4].severity).isEqualTo(ReportService.Severity.WARNING)
        assertThat(actual[5].severity).isEqualTo(ReportService.Severity.INFO)
    }

}