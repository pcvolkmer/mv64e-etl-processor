package dev.dnpm.etl.processor.consent;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import dev.dnpm.etl.processor.config.AppFhirConfig;
import dev.dnpm.etl.processor.config.GIcsConfigProperties;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4.model.StringType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.TerminatedRetryException;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;


public class GicsConsentService implements ICheckConsent {

    private final Logger log = LoggerFactory.getLogger(GicsConsentService.class);

    private final GIcsConfigProperties gIcsConfigProperties;

    public static final String IS_CONSENTED_ENDPOINT = "/$isConsented";
    private final RetryTemplate retryTemplate;
    private final RestTemplate restTemplate;
    private final FhirContext fhirContext;
    private final HttpHeaders httpHeader;
    private String url;


    public GicsConsentService(GIcsConfigProperties gIcsConfigProperties,
        RetryTemplate retryTemplate, RestTemplate restTemplate, AppFhirConfig appFhirConfig) {
        this.gIcsConfigProperties = gIcsConfigProperties;
        this.retryTemplate = retryTemplate;
        this.restTemplate = restTemplate;
        this.fhirContext = appFhirConfig.fhirContext();
        httpHeader = buildHeader(gIcsConfigProperties.getUsername(),
            gIcsConfigProperties.getPassword());
    }

    public String getGicsUri() {
        if (url == null) {
            final String gIcsBaseUri = gIcsConfigProperties.getGIcsBaseUri();
            if (StringUtils.isBlank(gIcsBaseUri)) {
                throw new IllegalArgumentException(
                    "gICS base URL is empty - should call gICS with false configuration.");
            }
            url = UriComponentsBuilder.fromHttpUrl(gIcsBaseUri).path(IS_CONSENTED_ENDPOINT)
                .toUriString();
        }
        return url;
    }

    @NotNull
    private static HttpHeaders buildHeader(String gPasUserName, String gPasPassword) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);

        if (StringUtils.isBlank(gPasUserName) || StringUtils.isBlank(gPasPassword)) {
            return headers;
        }

        headers.setBasicAuth(gPasUserName, gPasPassword);
        return headers;
    }

    public static Parameters getIsConsentedParam(GIcsConfigProperties configProperties,
        String personIdentifierValue) {
        var result = new Parameters();
        result.addParameter(new ParametersParameterComponent().setName("personIdentifier").setValue(
            new Identifier().setValue(personIdentifierValue)
                .setSystem(configProperties.getPersonIdentifierSystem())));
        result.addParameter(new ParametersParameterComponent().setName("domain")
            .setValue(new StringType().setValue(configProperties.getConsentDomainName())));
        result.addParameter(new ParametersParameterComponent().setName("policy").setValue(
            new Coding().setCode(configProperties.getPolicyCode())
                .setSystem(configProperties.getPolicySystem())));

        /*
         * is mandatory parameter, but we ignore it via additional configuration parameter
         * 'ignoreVersionNumber'.
         */
        result.addParameter(new ParametersParameterComponent().setName("version")
            .setValue(new StringType().setValue("1.1")));

        /* add config parameter with:
         * ignoreVersionNumber -> true ->> Reason is we cannot know which policy version each patient
         * has possibly signed or not, therefore we are happy with any version found.
         * unknownStateIsConsideredAsDecline -> true
         */
        var config = new ParametersParameterComponent().setName("config").addPart(
            new ParametersParameterComponent().setName("ignoreVersionNumber")
                .setValue(new BooleanType().setValue(true))).addPart(
            new ParametersParameterComponent().setName("unknownStateIsConsideredAsDecline")
                .setValue(new BooleanType().setValue(true)));
        result.addParameter(config);

        return result;
    }

    protected String getConsentStatusResponse(Parameters parameter) {
        var parameterAsXml = fhirContext.newXmlParser().encodeResourceToString(parameter);

        HttpEntity<String> requestEntity = new HttpEntity<>(parameterAsXml, this.httpHeader);
        ResponseEntity<String> responseEntity;
        try {
            var url = getGicsUri();

            responseEntity = retryTemplate.execute(
                ctx -> restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class));
        } catch (RestClientException e) {
            var msg = String.format("Get consents status request failed reason: '%s",
                e.getMessage());
            log.error(msg);
            return null;

        } catch (TerminatedRetryException terminatedRetryException) {
            var msg = String.format(
                "Get consents status process has been terminated. termination reason: '%s",
                terminatedRetryException.getMessage());
            log.error(msg);
            return null;

        }
        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            return responseEntity.getBody();
        } else {
            var msg = String.format(
                "Trusted party system reached but request failed! code: '%s' response: '%s'",
                responseEntity.getStatusCode(), responseEntity.getBody());
            log.error(msg);
            return null;
        }
    }

    @Override
    public TtpConsentStatus isConsented(String personIdentifierValue) {
        var parameter = GicsConsentService.getIsConsentedParam(gIcsConfigProperties,
            personIdentifierValue);

        var consentStatusResponse = getConsentStatusResponse(parameter);
        return evaluateConsentResponse(consentStatusResponse);

    }

    private TtpConsentStatus evaluateConsentResponse(String consentStatusResponse) {
        if (consentStatusResponse == null) {
            return TtpConsentStatus.FAILED_TO_ASK;
        }
        try {
            var response = fhirContext.newJsonParser().parseResource(consentStatusResponse);

            if (response instanceof Parameters responseParameters) {

                var responseValue = responseParameters.getParameter("consented").getValue();
                var isConsented = responseValue.castToBoolean(responseValue);
                if (!isConsented.hasValue()) {
                    return TtpConsentStatus.FAILED_TO_ASK;
                }
                if (isConsented.booleanValue()) {
                    return TtpConsentStatus.CONSENTED;
                } else {
                    return TtpConsentStatus.CONSENT_MISSING_OR_REJECTED;
                }
            } else if (response instanceof OperationOutcome outcome) {

                log.error(
                    "failed to get consent status from ttp. probably configuration error. outcome: ",
                    fhirContext.newJsonParser().encodeToString(outcome));

            }
        } catch (DataFormatException dfe) {
            log.error("failed to parse response to FHIR R4 resource.", dfe);
        }
        return TtpConsentStatus.FAILED_TO_ASK;
    }
}
