package dev.dnpm.etl.processor.consent;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dnpm.etl.processor.config.GIcsConfigProperties;
import dev.pcvolkmer.mv64e.mtb.ConsentProvision;
import dev.pcvolkmer.mv64e.mtb.ModelProjectConsentPurpose;
import dev.pcvolkmer.mv64e.mtb.Mtb;
import dev.pcvolkmer.mv64e.mtb.Provision;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Consent.ProvisionComponent;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseConsentService implements ICheckConsent {

    protected final GIcsConfigProperties gIcsConfigProperties;

    private final ObjectMapper objectMapper;
    protected Logger logger = LoggerFactory.getLogger(BaseConsentService.class);
    static FhirContext fhirCtx = FhirContext.forR4();

    public BaseConsentService(GIcsConfigProperties gIcsConfigProperties,
        ObjectMapper objectMapper) {
        this.gIcsConfigProperties = gIcsConfigProperties;
        this.objectMapper = objectMapper;
    }

    public void embedBroadConsentResources(Mtb mtbFile, Bundle broadConsent) {

        for (Bundle.BundleEntryComponent entry : broadConsent.getEntry()) {
            Resource resource = entry.getResource();
            if (resource instanceof Consent) {
                // since jackson convertValue does not work here,
                // we need another step to back to string, before we convert to object map
                var asJsonString = fhirCtx.newJsonParser().encodeResourceToString(resource);
                try {
                    var mapOfJson = objectMapper.readValue(asJsonString,
                        new TypeReference<HashMap<String, Object>>() {
                        });
                    mtbFile.getMetadata().getResearchConsents().add(mapOfJson);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public void addGenomeDbProvisions(Mtb mtbFile, Bundle consentGnomeDe) {
        for (Bundle.BundleEntryComponent entry : consentGnomeDe.getEntry()) {
            Resource resource = entry.getResource();
            if (!(resource instanceof Consent consentFhirResource)) {
                continue;
            }

            // We expect only one provision in collection, therefore get first or none
            List<ProvisionComponent> provisions = consentFhirResource.getProvision().getProvision();
            if (provisions.isEmpty()) {
                continue;
            }

            var provisionComponent = provisions.getFirst();

            String provisionCode = null;
            if (provisionComponent.getCode() != null && !provisionComponent.getCode().isEmpty()) {
                CodeableConcept codeableConcept = provisionComponent.getCode().getFirst();
                if (codeableConcept.getCoding() != null && !codeableConcept.getCoding().isEmpty()) {
                    provisionCode = codeableConcept.getCoding().getFirst().getCode();
                }
            }

            if (provisionCode != null) {
                try {
                    ModelProjectConsentPurpose modelProjectConsentPurpose =
                        ModelProjectConsentPurpose.forValue(provisionCode);

                    if (ModelProjectConsentPurpose.SEQUENCING.equals(modelProjectConsentPurpose)) {
                        // CONVENTION: wrapping date is date of SEQUENCING consent
                        mtbFile.getMetadata().getModelProjectConsent()
                            .setDate(consentFhirResource.getDateTime());
                    }

                    Provision provision = Provision.builder()
                        .type(ConsentProvision.valueOf(provisionComponent.getType().name()))
                        .date(provisionComponent.getPeriod().getStart())
                        .purpose(modelProjectConsentPurpose)
                        .build();

                    mtbFile.getMetadata().getModelProjectConsent().getProvisions().add(provision);

                } catch (IOException ioe) {
                    logger.error(
                        "Provision code '" + provisionCode + "' is unknown and cannot be mapped.",
                        ioe.toString());
                }
            }

            if (!mtbFile.getMetadata().getModelProjectConsent().getProvisions().isEmpty()) {
                mtbFile.getMetadata().getModelProjectConsent()
                    .setVersion(gIcsConfigProperties.getGenomeDeConsentVersion());
            }
        }
    }
}
