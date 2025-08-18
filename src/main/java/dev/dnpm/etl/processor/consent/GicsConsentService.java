package dev.dnpm.etl.processor.consent;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import dev.dnpm.etl.processor.config.AppFhirConfig;
import dev.dnpm.etl.processor.config.GIcsConfigProperties;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.retry.TerminatedRetryException;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Date;

/**
 * Service to request Consent from remote gICS installation
 *
 * @since 0.11
 */
public class GicsConsentService implements IConsentService {

    private final Logger log = LoggerFactory.getLogger(GicsConsentService.class);

    public static final String IS_CONSENTED_ENDPOINT = "/$isConsented";
    public static final String IS_POLICY_STATES_FOR_PERSON_ENDPOINT = "/$currentPolicyStatesForPerson";

    private final RetryTemplate retryTemplate;
    private final RestTemplate restTemplate;
    private final FhirContext fhirContext;
    private final GIcsConfigProperties gIcsConfigProperties;

    public GicsConsentService(
        GIcsConfigProperties gIcsConfigProperties,
        RetryTemplate retryTemplate,
        RestTemplate restTemplate,
        AppFhirConfig appFhirConfig
    ) {
        this.retryTemplate = retryTemplate;
        this.restTemplate = restTemplate;
        this.fhirContext = appFhirConfig.fhirContext();
        this.gIcsConfigProperties = gIcsConfigProperties;
        log.info("GicsConsentService initialized...");
    }

    protected Parameters getFhirRequestParameters(
        String personIdentifierValue
    ) {
        var result = new Parameters();
        result.addParameter(
            new ParametersParameterComponent()
                .setName("personIdentifier")
                .setValue(
                    new Identifier()
                        .setValue(personIdentifierValue)
                        .setSystem(this.gIcsConfigProperties.getPersonIdentifierSystem())
                )
        );
        result.addParameter(
            new ParametersParameterComponent()
                .setName("domain")
                .setValue(
                    new StringType()
                        .setValue(this.gIcsConfigProperties.getBroadConsentDomainName())
                )
        );
        result.addParameter(
            new ParametersParameterComponent()
                .setName("policy")
                .setValue(
                    new Coding()
                        .setCode(this.gIcsConfigProperties.getBroadConsentPolicyCode())
                        .setSystem(this.gIcsConfigProperties.getBroadConsentPolicySystem())
                )
        );

        /*
         * is mandatory parameter, but we ignore it via additional configuration parameter
         * 'ignoreVersionNumber'.
         */
        result.addParameter(
            new ParametersParameterComponent()
                .setName("version")
                .setValue(new StringType().setValue("1.1")
                )
        );

        /* add config parameter with:
         * ignoreVersionNumber -> true ->> Reason is we cannot know which policy version each patient
         * has possibly signed or not, therefore we are happy with any version found.
         * unknownStateIsConsideredAsDecline -> true
         */
        var config = new ParametersParameterComponent()
            .setName("config")
            .addPart(
                new ParametersParameterComponent()
                    .setName("ignoreVersionNumber")
                    .setValue(new BooleanType().setValue(true))
            )
            .addPart(
                new ParametersParameterComponent()
                    .setName("unknownStateIsConsideredAsDecline")
                    .setValue(new BooleanType().setValue(false))
            );

        result.addParameter(config);

        return result;
    }

    private URI endpointUri(String endpoint) {
        assert this.gIcsConfigProperties.getUri() != null;
        return UriComponentsBuilder.fromUriString(this.gIcsConfigProperties.getUri()).path(endpoint).build().toUri();
    }

    private HttpHeaders headersWithHttpBasicAuth() {
        assert this.gIcsConfigProperties.getUri() != null;

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);

        if (
            StringUtils.isBlank(this.gIcsConfigProperties.getUsername())
                || StringUtils.isBlank(this.gIcsConfigProperties.getPassword())
        ) {
            return headers;
        }

        headers.setBasicAuth(this.gIcsConfigProperties.getUsername(), this.gIcsConfigProperties.getPassword());
        return headers;
    }

    protected String callGicsApi(Parameters parameter, String endpoint) {
        var parameterAsXml = fhirContext.newXmlParser().encodeResourceToString(parameter);
        HttpEntity<String> requestEntity = new HttpEntity<>(parameterAsXml, this.headersWithHttpBasicAuth());
        try {
            var responseEntity = retryTemplate.execute(
                ctx -> restTemplate.exchange(endpointUri(endpoint), HttpMethod.POST, requestEntity, String.class)
            );

            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                return responseEntity.getBody();
            } else {
                var msg = String.format(
                    "Trusted party system reached but request failed! code: '%s' response: '%s'",
                    responseEntity.getStatusCode(), responseEntity.getBody());
                log.error(msg);
                return null;
            }
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
    }

    @Override
    public TtpConsentStatus getTtpBroadConsentStatus(String personIdentifierValue) {
        var consentStatusResponse = callGicsApi(
            getFhirRequestParameters(personIdentifierValue),
            GicsConsentService.IS_CONSENTED_ENDPOINT
        );
        return evaluateConsentResponse(consentStatusResponse);
    }

    protected Bundle currentConsentForPersonAndTemplate(
        String personIdentifierValue,
        ConsentDomain consentDomain,
        Date requestDate
    ) {

        var requestParameter = buildRequestParameterCurrentPolicyStatesForPerson(
            personIdentifierValue,
            requestDate,
            consentDomain
        );

        var consentDataSerialized = callGicsApi(requestParameter,
                                                GicsConsentService.IS_POLICY_STATES_FOR_PERSON_ENDPOINT);

        if (consentDataSerialized == null) {
            // error occurred - should not process further!
            throw new IllegalStateException(
                "consent data request failed - stopping processing! - try again or fix other problems first.");
        }
        var iBaseResource = fhirContext.newJsonParser()
                                       .parseResource(consentDataSerialized);
        if (iBaseResource instanceof OperationOutcome) {
            // log error  - very likely a configuration error
            String errorMessage =
                "Consent request failed! Check outcome:\n " + consentDataSerialized;
            log.error(errorMessage);
            throw new IllegalStateException(errorMessage);
        } else if (iBaseResource instanceof Bundle bundle) {
            return bundle;
        } else {
            String errorMessage = "Consent request failed! Unexpected response received! ->  "
                + consentDataSerialized;
            log.error(errorMessage);
            throw new IllegalStateException(errorMessage);
        }
    }

    @NotNull
    private String getConsentDomainName(ConsentDomain targetConsentDomain) {
        return switch (targetConsentDomain) {
            case BROAD_CONSENT -> gIcsConfigProperties.getBroadConsentDomainName();
            case MODELLVORHABEN_64E -> gIcsConfigProperties.getGenomDeConsentDomainName();
        };
    }

    protected Parameters buildRequestParameterCurrentPolicyStatesForPerson(
        String personIdentifierValue,
        Date requestDate,
        ConsentDomain consentDomain
    ) {
        var requestParameter = new Parameters();
        requestParameter.addParameter(
            new ParametersParameterComponent()
                .setName("personIdentifier")
                .setValue(
                    new Identifier()
                        .setValue(personIdentifierValue)
                        .setSystem(this.gIcsConfigProperties.getPersonIdentifierSystem())
                )
        );

        requestParameter.addParameter(
            new ParametersParameterComponent()
                .setName("domain")
                .setValue(new StringType().setValue(getConsentDomainName(consentDomain)))
        );

        Parameters nestedConfigParameters = new Parameters();
        nestedConfigParameters
            .addParameter(
                new ParametersParameterComponent()
                    .setName("idMatchingType")
                    .setValue(new Coding()
                                  .setSystem("https://ths-greifswald.de/fhir/CodeSystem/gics/IdMatchingType")
                                  .setCode("AT_LEAST_ONE")
                    )
            )
            .addParameter("ignoreVersionNumber", false)
            .addParameter("unknownStateIsConsideredAsDecline", false)
            .addParameter("requestDate", new DateType().setValue(requestDate));

        requestParameter.addParameter(
            new ParametersParameterComponent().setName("config").addPart().setResource(nestedConfigParameters)
        );

        return requestParameter;
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
                    return TtpConsentStatus.BROAD_CONSENT_GIVEN;
                } else {
                    return TtpConsentStatus.BROAD_CONSENT_MISSING_OR_REJECTED;
                }
            } else if (response instanceof OperationOutcome outcome) {
                log.error("failed to get consent status from ttp. probably configuration error. "
                              + "outcome: '{}'", fhirContext.newJsonParser().encodeToString(outcome));

            }
        } catch (DataFormatException dfe) {
            log.error("failed to parse response to FHIR R4 resource.", dfe);
        }
        return TtpConsentStatus.FAILED_TO_ASK;
    }

    @Override
    public Bundle getConsent(String patientId, Date requestDate, ConsentDomain consentDomain) {
        return currentConsentForPersonAndTemplate(patientId, consentDomain, requestDate);
    }
}
