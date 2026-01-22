package dev.dnpm.etl.processor.consent;

import dev.dnpm.etl.processor.config.AppFhirConfig;
import dev.dnpm.etl.processor.config.GIcsConfigProperties;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import kotlin.random.Random;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.net.URIBuilder;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.retry.TerminatedRetryException;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Service to request Consent from remote gICS installation
 *
 * @since 0.11
 */
public class GicsConsentService extends AbstractConsentService {

  public static final String IS_CONSENTED_ENDPOINT = "/$isConsented";
  public static final String IS_POLICY_STATES_FOR_PERSON_ENDPOINT =
      "/$currentPolicyStatesForPerson";
  private static final String BROAD_CONSENT_PROFILE_URI =
      "https://www.medizininformatik-initiative.de/fhir/modul-consent/StructureDefinition/mii-pr-consent-einwilligung";

  private final RetryTemplate retryTemplate;
  private final RestTemplate restTemplate;
  private final GIcsConfigProperties gIcsConfigProperties;

  public GicsConsentService(
      GIcsConfigProperties gIcsConfigProperties,
      RetryTemplate retryTemplate,
      RestTemplate restTemplate,
      AppFhirConfig appFhirConfig) {
    super(appFhirConfig.fhirContext(), LoggerFactory.getLogger(GicsConsentService.class));

    this.retryTemplate = retryTemplate;
    this.restTemplate = restTemplate;
    this.gIcsConfigProperties = gIcsConfigProperties;
    log.info("GicsConsentService initialized...");
  }

  protected Parameters getFhirRequestParameters(String personIdentifierValue) {
    var result = new Parameters();
    result.addParameter(
        new ParametersParameterComponent()
            .setName("personIdentifier")
            .setValue(
                new Identifier()
                    .setValue(personIdentifierValue)
                    .setSystem(this.gIcsConfigProperties.getPersonIdentifierSystem())));
    result.addParameter(
        new ParametersParameterComponent()
            .setName("domain")
            .setValue(
                new StringType().setValue(this.gIcsConfigProperties.getBroadConsentDomainName())));
    result.addParameter(
        new ParametersParameterComponent()
            .setName("policy")
            .setValue(
                new Coding()
                    .setCode(this.gIcsConfigProperties.getBroadConsentPolicyCode())
                    .setSystem(this.gIcsConfigProperties.getBroadConsentPolicySystem())));

    /*
     * is mandatory parameter, but we ignore it via additional configuration parameter
     * 'ignoreVersionNumber'.
     */
    result.addParameter(
        new ParametersParameterComponent()
            .setName("version")
            .setValue(new StringType().setValue("1.1")));

    /* add config parameter with:
     * ignoreVersionNumber -> true ->> Reason is we cannot know which policy version each patient
     * has possibly signed or not, therefore we are happy with any version found.
     * unknownStateIsConsideredAsDecline -> true
     */
    var config =
        new ParametersParameterComponent()
            .setName("config")
            .addPart(
                new ParametersParameterComponent()
                    .setName("ignoreVersionNumber")
                    .setValue(new BooleanType().setValue(true)))
            .addPart(
                new ParametersParameterComponent()
                    .setName("unknownStateIsConsideredAsDecline")
                    .setValue(new BooleanType().setValue(false)));

    result.addParameter(config);

    return result;
  }

  private URI endpointUri(String endpoint) throws URISyntaxException {
    if (null == this.gIcsConfigProperties.getUri()) {
      throw new URISyntaxException("null", "URI must not be null");
    }
    var gPasUrl1 = this.gIcsConfigProperties.getUri();
    if (this.gIcsConfigProperties.getUri().lastIndexOf("/")
        == this.gIcsConfigProperties.getUri().length() - 1) {
      gPasUrl1 =
          this.gIcsConfigProperties
              .getUri()
              .substring(0, this.gIcsConfigProperties.getUri().length() - 1);
    }
    var urlBuilder = new URIBuilder(new URI(gPasUrl1)).appendPath(endpoint);

    return urlBuilder.build();
  }

  private HttpHeaders headersWithHttpBasicAuth() {
    assert this.gIcsConfigProperties.getUri() != null;

    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_XML);

    if (StringUtils.isBlank(this.gIcsConfigProperties.getUsername())
        || StringUtils.isBlank(this.gIcsConfigProperties.getPassword())) {
      return headers;
    }

    headers.setBasicAuth(
        this.gIcsConfigProperties.getUsername(), this.gIcsConfigProperties.getPassword());
    return headers;
  }

  @Nullable
  protected String callGicsApi(Parameters parameter, String endpoint) {
    var parameterAsXml = fhirContext.newXmlParser().encodeResourceToString(parameter);
    HttpEntity<String> requestEntity =
        new HttpEntity<>(parameterAsXml, this.headersWithHttpBasicAuth());
    try {
      var responseEntity =
          retryTemplate.execute(
              ctx ->
                  restTemplate.exchange(
                      endpointUri(endpoint), HttpMethod.POST, requestEntity, String.class));

      if (responseEntity.getStatusCode().is2xxSuccessful()) {
        return responseEntity.getBody();
      } else {
        var msg =
            String.format(
                "Trusted party system reached but request failed! code: '%s' response: '%s'",
                responseEntity.getStatusCode(), responseEntity.getBody());
        log.error(msg);
        return null;
      }
    } catch (RestClientException e) {
      var msg = String.format("Get consents status request failed reason: '%s", e.getMessage());
      log.error(msg);
      return null;

    } catch (TerminatedRetryException terminatedRetryException) {
      var msg =
          String.format(
              "Get consents status process has been terminated. termination reason: '%s",
              terminatedRetryException.getMessage());
      log.error(msg);
      return null;
    } catch (URISyntaxException e) {
      var msg = String.format("Invalid URI for consents status request: '%s", e.getMessage());
      log.error(msg);
      return null;
    }
  }

  @Override
  @NonNull
  public TtpConsentStatus getTtpBroadConsentStatus(@NonNull String personIdentifierValue) {
    var consentStatusResponse =
        callGicsApi(
            getFhirRequestParameters(personIdentifierValue),
            GicsConsentService.IS_CONSENTED_ENDPOINT);
    return evaluateConsentResponse(consentStatusResponse);
  }

  protected Bundle currentConsentForPersonAndTemplate(
      String personIdentifierValue, ConsentDomain consentDomain, Date requestDate) {

    var requestParameter =
        buildRequestParameterCurrentPolicyStatesForPerson(
            personIdentifierValue, requestDate, consentDomain);

    var consentDataSerialized =
        callGicsApi(requestParameter, GicsConsentService.IS_POLICY_STATES_FOR_PERSON_ENDPOINT);

    if (consentDataSerialized == null) {
      // error occurred - should not process further!
      throw new IllegalStateException(
          "consent data request failed - stopping processing! - try again or fix other problems first.");
    }
    var iBaseResource = fhirContext.newJsonParser().parseResource(consentDataSerialized);
    if (iBaseResource instanceof OperationOutcome) {
      // log error  - very likely a configuration error
      String errorMessage = "Consent request failed! Check outcome:\n " + consentDataSerialized;
      log.error(errorMessage);
      throw new IllegalStateException(errorMessage);
    } else if (iBaseResource instanceof Bundle bundle) {
      return bundle;
    } else {
      String errorMessage =
          "Consent request failed! Unexpected response received! ->  " + consentDataSerialized;
      log.error(errorMessage);
      throw new IllegalStateException(errorMessage);
    }
  }

  @Nullable
  private String getConsentDomainName(ConsentDomain targetConsentDomain) {
    return switch (targetConsentDomain) {
      case BROAD_CONSENT -> gIcsConfigProperties.getBroadConsentDomainName();
      case MODELLVORHABEN_64E -> gIcsConfigProperties.getGenomDeConsentDomainName();
    };
  }

  protected Parameters buildRequestParameterCurrentPolicyStatesForPerson(
      String personIdentifierValue, Date requestDate, ConsentDomain consentDomain) {
    var requestParameter = new Parameters();
    requestParameter.addParameter(
        new ParametersParameterComponent()
            .setName("personIdentifier")
            .setValue(
                new Identifier()
                    .setValue(personIdentifierValue)
                    .setSystem(this.gIcsConfigProperties.getPersonIdentifierSystem())));

    requestParameter.addParameter(
        new ParametersParameterComponent()
            .setName("domain")
            .setValue(new StringType().setValue(getConsentDomainName(consentDomain))));

    Parameters nestedConfigParameters = new Parameters();
    nestedConfigParameters
        .addParameter(
            new ParametersParameterComponent()
                .setName("idMatchingType")
                .setValue(
                    new Coding()
                        .setSystem("https://ths-greifswald.de/fhir/CodeSystem/gics/IdMatchingType")
                        .setCode("AT_LEAST_ONE")))
        .addParameter("ignoreVersionNumber", true)
        .addParameter("unknownStateIsConsideredAsDecline", false)
        .addParameter("requestDate", new DateType().setValue(requestDate));

    requestParameter.addParameter(
        new ParametersParameterComponent()
            .setName("config")
            .addPart()
            .setResource(nestedConfigParameters));

    return requestParameter;
  }

  @Override
  @NonNull
  public Bundle getConsent(
      @NonNull String patientId, @NonNull Date requestDate, @NonNull ConsentDomain consentDomain) {
    Bundle gIcsResultBundle =
        currentConsentForPersonAndTemplate(patientId, consentDomain, requestDate);
    if (ConsentDomain.BROAD_CONSENT == consentDomain) {
      return anonymizeBroadConsent(convertGicsResultToMiiBroadConsent(gIcsResultBundle));
    }
    return gIcsResultBundle;
  }

  protected Bundle convertGicsResultToMiiBroadConsent(Bundle gIcsResultBundle) {
    if (gIcsResultBundle == null
        || gIcsResultBundle.getEntry().isEmpty()
        || !(gIcsResultBundle.getEntry().getFirst().getResource() instanceof Consent))
      return gIcsResultBundle;

    Bundle.BundleEntryComponent bundleEntryComponent = gIcsResultBundle.getEntry().getFirst();

    var consentAsOne = (Consent) bundleEntryComponent.getResource();

    if (isMiiConsent(consentAsOne)) {
      return gIcsResultBundle;
    }

    if (consentAsOne.getPolicy().stream()
        .noneMatch(p -> p.getUri().equals(gIcsConfigProperties.getBroadConsentPolicyUri()))) {
      consentAsOne.addPolicy(
          new Consent.ConsentPolicyComponent()
              .setUri(gIcsConfigProperties.getBroadConsentPolicyUri()));
    }

    if (consentAsOne.getMeta().getProfile().stream()
        .noneMatch(p -> p.getValue().equals(BROAD_CONSENT_PROFILE_URI))) {
      consentAsOne.getMeta().addProfile(BROAD_CONSENT_PROFILE_URI);
    }

    consentAsOne.setPolicyRule(null);

    consentAsOne
        .getCategory()
        .removeIf(
            category ->
                category.hasCoding(
                    "http://fhir.de/ConsentManagement/CodeSystem/ResultType", "policy"));

    final var miiConsentCategory = new CodeableConcept();
    miiConsentCategory.addCoding(
        new Coding()
            .setSystem(
                "https://www.medizininformatik-initiative.de/fhir/modul-consent/CodeSystem/mii-cs-consent-consent_category")
            .setCode("2.16.840.1.113883.3.1937.777.24.2.184"));
    consentAsOne.addCategory(miiConsentCategory);

    gIcsResultBundle.getEntry().stream()
        .skip(1)
        .forEach(
            c ->
                consentAsOne
                    .getProvision()
                    .addProvision(
                        ((Consent) c.getResource()).getProvision().getProvisionFirstRep()));

    gIcsResultBundle.getEntry().clear();
    gIcsResultBundle.addEntry(bundleEntryComponent);
    return gIcsResultBundle;
  }

  private static boolean isMiiConsent(Consent consent) {
    for (var category : consent.getCategory()) {
      for (var categoryCoding : category.getCoding()) {
        if ("https://www.medizininformatik-initiative.de/fhir/modul-consent/CodeSystem/mii-cs-consent-consent_category"
                .equals(categoryCoding.getSystem())
            && "2.16.840.1.113883.3.1937.777.24.2.184".equals(categoryCoding.getCode())) {
          return true;
        }
      }
    }
    return false;
  }

  protected Bundle anonymizeBroadConsent(Bundle bundle) {
    Bundle.BundleEntryComponent bundleEntryComponent = bundle.getEntry().getFirst();
    hashBundleEntry(bundleEntryComponent);
    return bundle;
  }

  private static void hashBundleEntry(Bundle.BundleEntryComponent entry) {
    String id = entry.getResource().getIdPart();
    var hash = DigestUtils.sha256Hex("%s_%s".formatted(Random.Default.toString(), id));

    entry.getResource().setId(hash);
    entry.setFullUrl(entry.getFullUrl().replace(id, hash));
    var consent = (Consent) entry.getResource();
    consent
        .getSource()
        .setProperty("reference", new StringType("QuestionnaireResponse/%s".formatted(hash)));
  }
}
