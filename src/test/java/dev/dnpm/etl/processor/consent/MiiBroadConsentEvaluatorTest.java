/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2023       Comprehensive Cancer Center Mainfranken
 * Copyright (c) 2026  Paul-Christian Volkmer, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
