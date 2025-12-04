package dev.dnpm.etl.processor.consent;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Stream;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MiiBroadConsentEvaluatorTest {

  @ParameterizedTest
  @MethodSource("consentData")
  void shouldEvaluateResponse(String filename, TtpConsentStatus status) throws Exception {
    var inputStream =
        Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(filename));

    var actual =
        MiiBroadConsentEvaluator.evaluate(
            FhirContext.forR4(), IOUtils.toString(inputStream, StandardCharsets.UTF_8));

    assertThat(actual).isEqualTo(status);
  }

  public static Stream<Arguments> consentData() {
    return Stream.of(
        Arguments.of(
            "fake_broadConsent_mii_response_permit.json", TtpConsentStatus.BROAD_CONSENT_GIVEN),
        Arguments.of(
            "fake_broadConsent_mii_response_deny.json",
            TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED));
  }
}
