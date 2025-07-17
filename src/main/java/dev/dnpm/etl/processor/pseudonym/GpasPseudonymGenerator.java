/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2024  Comprehensive Cancer Center Mainfranken, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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

package dev.dnpm.etl.processor.pseudonym;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import dev.dnpm.etl.processor.config.AppFhirConfig;
import dev.dnpm.etl.processor.config.GPasConfigProperties;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4.model.StringType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.HttpClientErrorException.BadRequest;
import org.springframework.web.client.HttpClientErrorException.Unauthorized;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

public class GpasPseudonymGenerator implements Generator {

    private final FhirContext r4Context;
    private final String gPasUrl;
    private final String psnTargetDomain;
    private final HttpHeaders httpHeader;
    private final RetryTemplate retryTemplate;
    private final Logger log = LoggerFactory.getLogger(GpasPseudonymGenerator.class);

    private final RestTemplate restTemplate;

    public GpasPseudonymGenerator(GPasConfigProperties gpasCfg, RetryTemplate retryTemplate,
        RestTemplate restTemplate, AppFhirConfig appFhirConfig) {
        this.retryTemplate = retryTemplate;
        this.restTemplate = restTemplate;
        this.gPasUrl = gpasCfg.getUri();
        this.psnTargetDomain = gpasCfg.getTarget();
        this.r4Context = appFhirConfig.fhirContext();
        httpHeader = getHttpHeaders(gpasCfg.getUsername(), gpasCfg.getPassword());

        log.debug(String.format("%s has been initialized", this.getClass().getName()));

    }

    @Override
    public String generate(String id) {
        var gPasRequestBody = getGpasRequestBody(id);
        var responseEntity = getGpasPseudonym(gPasRequestBody);
        var gPasPseudonymResult = (Parameters) r4Context.newJsonParser()
            .parseResource(responseEntity.getBody());

        return unwrapPseudonym(gPasPseudonymResult);
    }

    @NotNull
    public static String unwrapPseudonym(Parameters gPasPseudonymResult) {
        final var parameters = gPasPseudonymResult.getParameter().stream().findFirst();

        if (parameters.isEmpty()) {
            throw new PseudonymRequestFailed("Empty HL7 parameters, cannot find first one");
        }

        final var identifier = (Identifier) parameters.get().getPart().stream()
            .filter(a -> a.getName().equals("pseudonym"))
            .findFirst()
            .orElseGet(ParametersParameterComponent::new).getValue();

        // pseudonym
        return sanitizeValue(identifier.getValue());
    }

    /**
     * Allow only filename friendly values
     *
     * @param psnValue GAPS pseudonym value
     * @return cleaned up value
     */
    public static String sanitizeValue(String psnValue) {
        // pattern to match forbidden characters
        String forbiddenCharsRegex = "[\\\\/:*?\"<>|;]";

        // Replace all forbidden characters with underscores
        return psnValue.replaceAll(forbiddenCharsRegex, "_");
    }

    @NotNull
    protected ResponseEntity<String> getGpasPseudonym(String gPasRequestBody) {

        HttpEntity<String> requestEntity = new HttpEntity<>(gPasRequestBody, this.httpHeader);

        try {
            ResponseEntity<String> responseEntity = retryTemplate.execute(
                ctx -> restTemplate.exchange(gPasUrl, HttpMethod.POST, requestEntity,
                    String.class));
            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                log.debug("API request succeeded. Response: {}", responseEntity.getStatusCode());
                return responseEntity;
            }
        } catch (RestClientException rce) {
            if (rce instanceof BadRequest) {
                String msg = "gPas or request configuration is incorrect. Please check both."
                    + rce.getMessage();
                log.debug(
                    msg);
                throw new PseudonymRequestFailed(msg, rce);
            }
            if (rce instanceof Unauthorized) {
                var msg = "gPas access credentials are invalid  check your configuration. msg:  '%s".formatted(
                    rce.getMessage());
                log.error(msg);
                throw new PseudonymRequestFailed(msg, rce);
            }
        } catch (Exception unexpected) {
            throw new PseudonymRequestFailed(
                "API request due unexpected error unsuccessful gPas unsuccessful.", unexpected);
        }
        throw new PseudonymRequestFailed(
            "API request due unexpected error unsuccessful gPas unsuccessful.");

    }

    protected String getGpasRequestBody(String id) {
        var requestParameters = new Parameters();
        requestParameters.addParameter().setName("target")
            .setValue(new StringType().setValue(psnTargetDomain));
        requestParameters.addParameter().setName("original")
            .setValue(new StringType().setValue(id));
        final IParser iParser = r4Context.newJsonParser();
        return iParser.encodeResourceToString(requestParameters);
    }

    @NotNull
    protected HttpHeaders getHttpHeaders(String gPasUserName, String gPasPassword) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (StringUtils.isBlank(gPasUserName) || StringUtils.isBlank(gPasPassword)) {
            return headers;
        }

        headers.setBasicAuth(gPasUserName, gPasPassword);
        return headers;
    }
}
