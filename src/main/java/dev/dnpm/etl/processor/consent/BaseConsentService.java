package dev.dnpm.etl.processor.consent;

import dev.dnpm.etl.processor.config.GIcsConfigProperties;
import dev.pcvolkmer.mv64e.mtb.ConsentProvision;
import dev.pcvolkmer.mv64e.mtb.ModelProjectConsentPurpose;
import dev.pcvolkmer.mv64e.mtb.Mtb;
import dev.pcvolkmer.mv64e.mtb.Provision;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Consent.ProvisionComponent;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseConsentService implements ICheckConsent {

    protected final GIcsConfigProperties gIcsConfigProperties;
    protected Logger logger = LoggerFactory.getLogger(BaseConsentService.class);

    public BaseConsentService(GIcsConfigProperties gIcsConfigProperties) {
        this.gIcsConfigProperties = gIcsConfigProperties;
    }

    public void embedBroadConsentResources(Mtb mtbFile, Bundle broadConsent) {
        for (Bundle.BundleEntryComponent entry : broadConsent.getEntry()) {
            Resource resource = entry.getResource();
            if (resource instanceof Consent) {
                Map<String, Object> consentMap = new HashMap<>();
                consentMap.put(resource.getIdElement().getIdPart(), resource);
                mtbFile.getMetadata().getResearchConsents().add(consentMap);
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
                        mtbFile.getMetadata().getModelProjectConsent().setDate(consentFhirResource.getDateTime());
                    }

                    Provision provision = Provision.builder()
                        .type(ConsentProvision.valueOf(provisionComponent.getType().name()))
                        .date(provisionComponent.getPeriod().getStart())
                        .purpose(modelProjectConsentPurpose)
                        .build();

                    mtbFile.getMetadata().getModelProjectConsent().getProvisions().add(provision);

                } catch (IOException ioe) {
                    logger.error("Provision code '" + provisionCode + "' is unknown and cannot be mapped.", ioe.toString());
                }
            }

            if (!mtbFile.getMetadata().getModelProjectConsent().getProvisions().isEmpty()) {
                mtbFile.getMetadata().getModelProjectConsent().setVersion(gIcsConfigProperties.getGenomeDeConsentVersion());
            }
        }
    }
}
