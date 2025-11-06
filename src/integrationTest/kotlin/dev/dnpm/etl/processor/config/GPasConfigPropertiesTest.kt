package dev.dnpm.etl.processor.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.test.context.ContextConfiguration


@SpringBootTest
@ContextConfiguration(
    classes = [
        GPasConfigProperties::class,
    ]
)
class GPasConfigPropertiesTest {

    @EnableConfigurationProperties(GPasConfigProperties::class)
    class TestConfig {}

    @Test
    fun shouldUseConfiguredPatientDomainIfPidDomainGiven() {
        ApplicationContextRunner()
            .withUserConfiguration(TestConfig::class.java)
            .withPropertyValues(
                "app.pseudonymize.gpas.uri=http://localhost/",
                "app.pseudonymize.gpas.pid-domain=test-pid-domain"
            )
            .run { context ->
                val properties = context.getBean(GPasConfigProperties::class.java)

                assertThat(properties).isNotNull
                assertThat(properties.patientDomain).isEqualTo("test-pid-domain")
            }
    }

    @Test
    fun shouldUseConfiguredPatientDomain() {
        ApplicationContextRunner()
            .withUserConfiguration(TestConfig::class.java)
            .withPropertyValues(
                "app.pseudonymize.gpas.uri=http://localhost/",
                "app.pseudonymize.gpas.patient-domain=test-patient-domain"
            )
            .run { context ->
                val properties = context.getBean(GPasConfigProperties::class.java)

                assertThat(properties).isNotNull
                assertThat(properties.patientDomain).isEqualTo("test-patient-domain")
            }
    }

}