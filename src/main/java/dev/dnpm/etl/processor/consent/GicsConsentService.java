package dev.dnpm.etl.processor.consent;

import ca.uhn.fhir.context.FhirContext;
import dev.dnpm.etl.processor.config.AppFhirConfig;
import dev.dnpm.etl.processor.config.GIcsConfigProperties;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
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

    public final String IS_CONSENTED_PATH = "/ttp-fhir/fhir/gics/$isConsented";
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
            url = UriComponentsBuilder.fromHttpUrl(gIcsBaseUri)
                .path(IS_CONSENTED_PATH)
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
        result.addParameter(new ParametersParameterComponent().setName("policy")
            .setValue(new Coding().setCode(configProperties.getPolicyCode())
                .setSystem(configProperties.getPolicySystem())));
        result.addParameter(new ParametersParameterComponent().setName("version")
            .setValue(new StringType().setValue(configProperties.getParameterVersion())));
        return result;
    }

    protected String getConsentStatusResponse(Parameters parameter) {
        var parameterAsXml = fhirContext.newXmlParser().encodeResourceToString(parameter);

        HttpEntity<String> requestEntity = new HttpEntity<>(parameterAsXml, this.httpHeader);
        ResponseEntity<String> responseEntity;
        try {
            responseEntity = retryTemplate.execute(
                ctx -> restTemplate.exchange(getGicsUri(), HttpMethod.POST, requestEntity,
                    String.class));
        } catch (RestClientException e) {
            var msg = String.format("Get consents status request failed reason: '%s",
                e.getMessage());
            log.error(msg);
            return null;

        } catch (TerminatedRetryException terminatedRetryException) {
            var msg = String.format(
                "Get consents status process has been terminated. termination reason: '%s",
                terminatedRetryException.getMessage());
            log.error(msg
            );
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
    public ConsentStatus isConsented(String personIdentifierValue) {
        var parameter = GicsConsentService.getIsConsentedParam(gIcsConfigProperties,
            personIdentifierValue);

        var consentStatusResponse = getConsentStatusResponse(parameter);
        return evaluateConsentResponse(consentStatusResponse);

    }

    private ConsentStatus evaluateConsentResponse(String consentStatusResponse) {
        if (consentStatusResponse == null) {
            return ConsentStatus.FAILED_TO_ASK;
        }
        var responseParameters = fhirContext.newJsonParser()
            .parseResource(Parameters.class, consentStatusResponse);

        var responseValue = responseParameters.getParameter("consented").getValue();
        var isConsented = responseValue.castToBoolean(responseValue);
        if (!isConsented.hasValue()) {
            return ConsentStatus.FAILED_TO_ASK;
        }
        if (isConsented.booleanValue()) {
            return ConsentStatus.CONSENTED;
        } else {
            return ConsentStatus.CONSENT_MISSING;
        }
    }
}
