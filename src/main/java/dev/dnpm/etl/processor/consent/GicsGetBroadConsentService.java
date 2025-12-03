package dev.dnpm.etl.processor.consent;

import dev.dnpm.etl.processor.config.AppFhirConfig;
import dev.dnpm.etl.processor.config.GIcsConfigProperties;
import java.net.URISyntaxException;
import java.util.Date;
import org.apache.hc.core5.net.URIBuilder;
import org.hl7.fhir.r4.model.Bundle;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.retry.TerminatedRetryException;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Service to request Broad Consent only from remote gICS installation using REST/HTTP GET request
 *
 * @since 0.12
 */
@NullMarked
public class GicsGetBroadConsentService extends AbstractConsentService {

  private final RetryTemplate retryTemplate;
  private final RestTemplate restTemplate;
  private final GIcsConfigProperties gIcsConfigProperties;

  public GicsGetBroadConsentService(
      GIcsConfigProperties gIcsConfigProperties,
      RetryTemplate retryTemplate,
      RestTemplate restTemplate,
      AppFhirConfig appFhirConfig) {
    super(appFhirConfig.fhirContext(), LoggerFactory.getLogger(GicsGetBroadConsentService.class));

    this.retryTemplate = retryTemplate;
    this.restTemplate = restTemplate;
    this.gIcsConfigProperties = gIcsConfigProperties;

    if (null == this.gIcsConfigProperties.getUri()) {
      throw new IllegalStateException("Missing gICS URI configuration");
    }

    log.info("GicsGetBroadConsentService initialized...");
  }

  @Override
  public TtpConsentStatus getTtpBroadConsentStatus(String personIdentifierValue) {
    var consentStatusResponse =
        requestResponse(
            personIdentifierValue, this.gIcsConfigProperties.getBroadConsentDomainName());
    return evaluateConsentResponse(consentStatusResponse);
  }

  @Override
  public Bundle getConsent(
      String personIdentifierValue, Date requestDate, ConsentDomain consentDomain) {
    return fhirContext
        .newJsonParser()
        .parseResource(
            Bundle.class,
            requestResponse(
                personIdentifierValue, gIcsConfigProperties.getBroadConsentDomainName()));
  }

  @Nullable
  private String requestResponse(String personIdentifierValue, String consentDomain) {
    if (null == this.gIcsConfigProperties.getUri()) {
      throw new IllegalStateException("Missing gICS URI configuration");
    }

    final var patientIdentifierQueryValue =
        "%s|%s"
            .formatted(
                this.gIcsConfigProperties.getPersonIdentifierSystem(), personIdentifierValue);

    try {
      final var uri =
          new URIBuilder(gIcsConfigProperties.getUri())
              .appendPathSegments("Consent")
              .addParameter("domain:identifier", consentDomain)
              .addParameter(
                  "category",
                  "http://fhir.de/ConsentManagement/CodeSystem/ResultType|consent-status")
              .addParameter("patient.identifier", patientIdentifierQueryValue)
              .build();

      final var requestHeaders = new HttpHeaders();

      if (null != gIcsConfigProperties.getUsername()
          && null != gIcsConfigProperties.getPassword()
          && !gIcsConfigProperties.getUsername().isBlank()
          && !gIcsConfigProperties.getPassword().isBlank()) {
        requestHeaders.setBasicAuth(
            gIcsConfigProperties.getUsername(), gIcsConfigProperties.getPassword());
      }

      final var response =
          this.retryTemplate.execute(
              retryContext ->
                  this.restTemplate.exchange(
                      uri, HttpMethod.GET, new HttpEntity<>(requestHeaders), String.class));
      if (response.getStatusCode().is2xxSuccessful()) {
        return response.getBody();
      } else {
        var msg =
            String.format(
                "Trusted party system reached but request failed! code: '%s' response: '%s'",
                response.getStatusCode(), response.getBody());
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
}
