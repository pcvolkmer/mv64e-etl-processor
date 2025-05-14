package dev.dnpm.etl.processor.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dnpm.etl.processor.config.AppConfiguration;
import dev.dnpm.etl.processor.config.AppFhirConfig;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;


@ContextConfiguration(classes = {AppConfiguration.class, ObjectMapper.class})
@TestPropertySource(properties = {"app.consent.gics.enabled=true",
    "app.consent.gics.uri=http://localhost:8090/ttp-fhir/fhir/gics"})
@RestClientTest
public class GicsConsentServiceTest {

    public static final String GICS_BASE_URI = "http://localhost:8090/ttp-fhir/fhir/gics";
    @Autowired
    MockRestServiceServer mockRestServiceServer;

    @Autowired
    GicsConsentService gicsConsentService;

    @Autowired
    AppConfiguration appConfiguration;

    @Autowired
    AppFhirConfig appFhirConfig;

    @BeforeEach
    public void setUp() {
        mockRestServiceServer = MockRestServiceServer.createServer(appConfiguration.restTemplate());

    }

    @Test
    void getTtpConsentStatus() {
        final Parameters responseConsented = new Parameters().addParameter(
            new ParametersParameterComponent().setName("consented")
                .setValue(new BooleanType().setValue(true)));

        mockRestServiceServer.expect(
            requestTo("http://localhost:8090/ttp-fhir/fhir/gics"
                + GicsConsentService.IS_CONSENTED_ENDPOINT)).andRespond(
            withSuccess(appFhirConfig.fhirContext().newJsonParser()
                    .encodeResourceToString(responseConsented),
                MediaType.APPLICATION_JSON));

        var consentStatus = gicsConsentService.getTtpConsentStatus("123456");
        assertThat(consentStatus).isEqualTo(TtpConsentStatus.CONSENTED);
    }

    @Test
    void consentRevoced() {
        final Parameters responseRevoced = new Parameters().addParameter(
            new ParametersParameterComponent().setName("consented")
                .setValue(new BooleanType().setValue(false)));

        mockRestServiceServer.expect(
            requestTo("http://localhost:8090/ttp-fhir/fhir/gics"
                + GicsConsentService.IS_CONSENTED_ENDPOINT)).andRespond(
            withSuccess(appFhirConfig.fhirContext().newJsonParser()
                    .encodeResourceToString(responseRevoced),
                MediaType.APPLICATION_JSON));

        var consentStatus = gicsConsentService.getTtpConsentStatus("123456");
        assertThat(consentStatus).isEqualTo(TtpConsentStatus.CONSENT_MISSING_OR_REJECTED);
    }


    @Test
    void gicsParameterInvalid() {
        final OperationOutcome responseErrorOutcome = new OperationOutcome().addIssue(
            new OperationOutcomeIssueComponent().setSeverity(
                    IssueSeverity.ERROR).setCode(IssueType.PROCESSING)
                .setDiagnostics("Invalid policy parameter..."));

        mockRestServiceServer.expect(
            requestTo(GICS_BASE_URI + GicsConsentService.IS_CONSENTED_ENDPOINT)).andRespond(
            withSuccess(appFhirConfig.fhirContext().newJsonParser()
                    .encodeResourceToString(responseErrorOutcome),
                MediaType.APPLICATION_JSON));

        var consentStatus = gicsConsentService.getTtpConsentStatus("123456");
        assertThat(consentStatus).isEqualTo(TtpConsentStatus.FAILED_TO_ASK);
    }


}
