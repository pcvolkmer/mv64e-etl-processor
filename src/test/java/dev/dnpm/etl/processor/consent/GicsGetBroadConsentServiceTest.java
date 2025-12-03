package dev.dnpm.etl.processor.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dnpm.etl.processor.config.AppConfiguration;
import dev.dnpm.etl.processor.config.AppFhirConfig;
import dev.dnpm.etl.processor.config.GIcsConfigProperties;
import java.net.URI;
import org.apache.hc.core5.net.URIBuilder;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

@ContextConfiguration(classes = {AppConfiguration.class, ObjectMapper.class})
@TestPropertySource(
    properties = {
      "app.consent.service=gics",
      "app.consent.gics.uri=http://localhost:8090/ttp-fhir/fhir/gics"
    })
@RestClientTest
class GicsGetBroadConsentServiceTest {

  static final String GICS_BASE_URI = "http://localhost:8090/ttp-fhir/fhir/gics";

  MockRestServiceServer mockRestServiceServer;
  AppFhirConfig appFhirConfig;
  GIcsConfigProperties gIcsConfigProperties;

  GicsGetBroadConsentService service;

  static URI expectedGicsConsentedEndpoint() throws Exception {
    return new URIBuilder(URI.create(GICS_BASE_URI))
        .appendPath("/Consent")
        .addParameter("domain:identifier", "MII")
        .addParameter(
            "category", "http://fhir.de/ConsentManagement/CodeSystem/ResultType|consent-status")
        .addParameter(
            "patient.identifier",
            "https://ths-greifswald.de/fhir/gics/identifiers/Patienten-ID|123456")
        .build();
  }

  @BeforeEach
  void setUp(
      @Autowired AppFhirConfig appFhirConfig,
      @Autowired GIcsConfigProperties gIcsConfigProperties) {
    this.appFhirConfig = appFhirConfig;
    this.gIcsConfigProperties = gIcsConfigProperties;

    var restTemplate = new RestTemplate();

    this.mockRestServiceServer = MockRestServiceServer.createServer(restTemplate);
    this.service =
        new GicsGetBroadConsentService(
            this.gIcsConfigProperties,
            RetryTemplate.builder().maxAttempts(1).build(),
            restTemplate,
            this.appFhirConfig);
  }

  @Test
  void shouldReturnTtpBroadConsentStatus() throws Exception {
    final Parameters consentedResponse =
        new Parameters()
            .addParameter(
                new ParametersParameterComponent()
                    .setName("consented")
                    .setValue(new BooleanType().setValue(true)));

    mockRestServiceServer
        .expect(requestTo(expectedGicsConsentedEndpoint()))
        .andRespond(
            withSuccess(
                appFhirConfig
                    .fhirContext()
                    .newJsonParser()
                    .encodeResourceToString(consentedResponse),
                MediaType.APPLICATION_JSON));

    var consentStatus = service.getTtpBroadConsentStatus("123456");
    assertThat(consentStatus).isEqualTo(TtpConsentStatus.BROAD_CONSENT_GIVEN);
  }

  @Test
  void shouldReturnRevokedConsent() throws Exception {
    final Parameters revokedResponse =
        new Parameters()
            .addParameter(
                new ParametersParameterComponent()
                    .setName("consented")
                    .setValue(new BooleanType().setValue(false)));

    mockRestServiceServer
        .expect(requestTo(expectedGicsConsentedEndpoint()))
        .andRespond(
            withSuccess(
                appFhirConfig.fhirContext().newJsonParser().encodeResourceToString(revokedResponse),
                MediaType.APPLICATION_JSON));

    var consentStatus = service.getTtpBroadConsentStatus("123456");
    assertThat(consentStatus).isEqualTo(TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED);
  }

  @Test
  void shouldReturnInvalidParameterResponse() throws Exception {
    final OperationOutcome responseWithErrorOutcome =
        new OperationOutcome()
            .addIssue(
                new OperationOutcomeIssueComponent()
                    .setSeverity(IssueSeverity.ERROR)
                    .setCode(IssueType.PROCESSING)
                    .setDiagnostics("Invalid policy parameter..."));

    mockRestServiceServer
        .expect(requestTo(expectedGicsConsentedEndpoint()))
        .andRespond(
            withSuccess(
                appFhirConfig
                    .fhirContext()
                    .newJsonParser()
                    .encodeResourceToString(responseWithErrorOutcome),
                MediaType.APPLICATION_JSON));

    var consentStatus = service.getTtpBroadConsentStatus("123456");
    assertThat(consentStatus).isEqualTo(TtpConsentStatus.FAILED_TO_ASK);
  }

  @Test
  void shouldReturnRequestError() throws Exception {
    mockRestServiceServer
        .expect(requestTo(expectedGicsConsentedEndpoint()))
        .andRespond(withServerError());

    var consentStatus = service.getTtpBroadConsentStatus("123456");
    assertThat(consentStatus).isEqualTo(TtpConsentStatus.FAILED_TO_ASK);
  }
}
