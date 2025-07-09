package dev.dnpm.etl.processor.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dnpm.etl.processor.config.AppConfiguration;
import dev.dnpm.etl.processor.config.AppFhirConfig;
import dev.dnpm.etl.processor.config.GIcsConfigProperties;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Consent.ConsentProvisionType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.core.io.ClassPathResource;
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

    @Autowired
    GIcsConfigProperties gIcsConfigProperties;

    @BeforeEach
    public void setUp() {
        mockRestServiceServer = MockRestServiceServer.createServer(appConfiguration.restTemplate());

    }

    @Test
    void getTtpBroadConsentStatus() {
        final Parameters responseConsented = new Parameters().addParameter(
            new ParametersParameterComponent().setName("consented")
                .setValue(new BooleanType().setValue(true)));

        mockRestServiceServer.expect(
            requestTo("http://localhost:8090/ttp-fhir/fhir/gics"
                + GicsConsentService.IS_CONSENTED_ENDPOINT)).andRespond(
            withSuccess(appFhirConfig.fhirContext().newJsonParser()
                    .encodeResourceToString(responseConsented),
                MediaType.APPLICATION_JSON));

        var consentStatus = gicsConsentService.getTtpBroadConsentStatus("123456");
        assertThat(consentStatus).isEqualTo(TtpConsentStatus.BROAD_CONSENT_GIVEN);
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

        var consentStatus = gicsConsentService.getTtpBroadConsentStatus("123456");
        assertThat(consentStatus).isEqualTo(TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED);
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

        var consentStatus = gicsConsentService.getTtpBroadConsentStatus("123456");
        assertThat(consentStatus).isEqualTo(TtpConsentStatus.FAILED_TO_ASK);
    }

    @Test
    void buildRequestParameterCurrentPolicyStatesForPersonTest() {

        String pid = "12345678";
        var result = GicsConsentService.buildRequestParameterCurrentPolicyStatesForPerson(gIcsConfigProperties,
            pid, Date.from(Instant.now()),gIcsConfigProperties.getGenomDeConsentDomainName());

        assertThat(result.getParameter().size()).as("should contain 3 parameter resources").isEqualTo(3);

        assertThat(((StringType)result.getParameter("domain").getValue()).getValue()).isEqualTo(gIcsConfigProperties.getGenomDeConsentDomainName());
        assertThat(((Identifier)result.getParameter("personIdentifier").getValue()).getValue()).isEqualTo(pid);
    }


    @ParameterizedTest
    @CsvSource({
        "2.16.840.1.113883.3.1937.777.24.5.3.8,urn:oid:2.16.840.1.113883.3.1937.777.24.5.3,2025-07-23T00:00:00+02:00,PERMIT,expect permit",
        "2.16.840.1.113883.3.1937.777.24.5.3.8,urn:oid:2.16.840.1.113883.3.1937.777.24.5.3,2025-06-23T00:00:00+02:00,PERMIT,expect permit date is exactly on start",
        "2.16.840.1.113883.3.1937.777.24.5.3.8,urn:oid:2.16.840.1.113883.3.1937.777.24.5.3,2055-06-23T00:00:00+02:00,PERMIT,expect permit date is exactly on end",
        "2.16.840.1.113883.3.1937.777.24.5.3.8,urn:oid:2.16.840.1.113883.3.1937.777.24.5.3,2021-06-23T00:00:00+02:00,NULL,date is before start",
        "2.16.840.1.113883.3.1937.777.24.5.3.8,urn:oid:2.16.840.1.113883.3.1937.777.24.5.3,2060-06-23T00:00:00+02:00,NULL,date is after end",
        "2.16.840.1.113883.3.1937.777.24.5.3.8,XXXX,2025-07-23T00:00:00+02:00,NULL,system not found - therefore expect NULL",
        "2.16.840.1.113883.3.1937.777.24.5.3.27,urn:oid:2.16.840.1.113883.3.1937.777.24.5.3,2025-07-23T00:00:00+02:00,DENY,provision is denied"})
    void getProvisionTypeByPolicyCode(String code, String system, String timeStamp, String expected,
        String desc) {

        var testData = getDummyBroadConsent();

        Date requestDate = Date.from(OffsetDateTime.parse(timeStamp).toInstant());

        var result = gicsConsentService.getProvisionTypeByPolicyCode(testData, code, system, requestDate);
        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();

        assertThat(result.get()).as(desc).isEqualTo(ConsentProvisionType.valueOf(expected));
    }

    private Bundle getDummyBroadConsent() {

        InputStream bundle;
        try {
            bundle = new ClassPathResource(
                "fake_broadConsent_gics_response_permit.json").getInputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return FhirContext.forR4().newJsonParser().parseResource(Bundle.class, bundle);

    }

}
