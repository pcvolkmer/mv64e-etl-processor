package dev.dnpm.etl.processor.consent;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dnpm.etl.processor.config.AppConfiguration;
import dev.dnpm.etl.processor.config.AppFhirConfig;
import dev.dnpm.etl.processor.config.GIcsConfigProperties;
import org.apache.hc.core5.net.URIBuilder;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
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

import java.net.URI;
import java.time.Instant;
import java.util.Date;

import static dev.dnpm.etl.processor.consent.GicsConsentService.IS_CONSENTED_ENDPOINT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ContextConfiguration(classes = {AppConfiguration.class, ObjectMapper.class})
@TestPropertySource(properties = {
    "app.consent.service=gics",
    "app.consent.gics.uri=http://localhost:8090/ttp-fhir/fhir/gics"
})
@RestClientTest
class GicsConsentServiceTest {

    static final String GICS_BASE_URI = "http://localhost:8090/ttp-fhir/fhir/gics";

    MockRestServiceServer mockRestServiceServer;
    AppFhirConfig appFhirConfig;
    GIcsConfigProperties gIcsConfigProperties;

    GicsConsentService gicsConsentService;

    static URI expectedGicsConsentedEndpoint() throws Exception {
    return new URIBuilder(URI.create(GICS_BASE_URI)).appendPath(IS_CONSENTED_ENDPOINT).build();
  }

  @BeforeEach
    void setUp(
        @Autowired AppFhirConfig appFhirConfig,
        @Autowired GIcsConfigProperties gIcsConfigProperties
    ) {
        this.appFhirConfig = appFhirConfig;
        this.gIcsConfigProperties = gIcsConfigProperties;

        var restTemplate = new RestTemplate();

        this.mockRestServiceServer = MockRestServiceServer.createServer(restTemplate);
        this.gicsConsentService = new GicsConsentService(
            this.gIcsConfigProperties,
            RetryTemplate.builder().maxAttempts(1).build(),
            restTemplate,
            this.appFhirConfig
        );
    }

    @Test
    void shouldReturnTtpBroadConsentStatus() throws Exception {
        final Parameters consentedResponse = new Parameters()
            .addParameter(
                new ParametersParameterComponent()
                    .setName("consented")
                    .setValue(new BooleanType().setValue(true))
            );

        mockRestServiceServer
            .expect(
                requestTo(
                    expectedGicsConsentedEndpoint())
            )
            .andRespond(
                withSuccess(
                    appFhirConfig.fhirContext().newJsonParser().encodeResourceToString(consentedResponse),
                    MediaType.APPLICATION_JSON
                )
            );

        var consentStatus = gicsConsentService.getTtpBroadConsentStatus("123456");
        assertThat(consentStatus).isEqualTo(TtpConsentStatus.BROAD_CONSENT_GIVEN);
    }

    @Test
    void shouldReturnRevokedConsent() throws Exception {
        final Parameters revokedResponse = new Parameters()
            .addParameter(
                new ParametersParameterComponent()
                    .setName("consented")
                    .setValue(new BooleanType().setValue(false))
            );

        mockRestServiceServer
            .expect(
                requestTo(
                    expectedGicsConsentedEndpoint())
            )
            .andRespond(
                withSuccess(
                    appFhirConfig.fhirContext().newJsonParser().encodeResourceToString(revokedResponse),
                    MediaType.APPLICATION_JSON)
            );

        var consentStatus = gicsConsentService.getTtpBroadConsentStatus("123456");
        assertThat(consentStatus).isEqualTo(TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED);
    }


    @Test
    void shouldReturnInvalidParameterResponse() throws Exception {
        final OperationOutcome responseWithErrorOutcome = new OperationOutcome()
            .addIssue(
                new OperationOutcomeIssueComponent()
                    .setSeverity(IssueSeverity.ERROR)
                    .setCode(IssueType.PROCESSING)
                    .setDiagnostics("Invalid policy parameter...")
            );

        mockRestServiceServer
            .expect(
                requestTo(expectedGicsConsentedEndpoint())
            )
            .andRespond(
                withSuccess(
                    appFhirConfig.fhirContext().newJsonParser().encodeResourceToString(responseWithErrorOutcome),
                    MediaType.APPLICATION_JSON
                )
            );

        var consentStatus = gicsConsentService.getTtpBroadConsentStatus("123456");
        assertThat(consentStatus).isEqualTo(TtpConsentStatus.FAILED_TO_ASK);
    }

    @Test
    void shouldReturnRequestError() throws Exception {
        mockRestServiceServer
            .expect(
                requestTo(expectedGicsConsentedEndpoint())
            )
            .andRespond(
                withServerError()
            );

        var consentStatus = gicsConsentService.getTtpBroadConsentStatus("123456");
        assertThat(consentStatus).isEqualTo(TtpConsentStatus.FAILED_TO_ASK);
    }

    @Test
    void buildRequestParameterCurrentPolicyStatesForPersonTest() {
        String pid = "12345678";
        var result = gicsConsentService
            .buildRequestParameterCurrentPolicyStatesForPerson(
                pid,
                Date.from(Instant.now()),
                ConsentDomain.MODELLVORHABEN_64E
            );

        assertThat(result.getParameter())
            .as("should contain 3 parameter resources")
            .hasSize(3);

        assertThat(((StringType) result.getParameter("domain").getValue()).getValue())
            .isEqualTo(
                gIcsConfigProperties.getGenomDeConsentDomainName()
            );

        assertThat(((Identifier) result.getParameter("personIdentifier").getValue()).getValue())
            .isEqualTo(
                pid
            );
    }


}
