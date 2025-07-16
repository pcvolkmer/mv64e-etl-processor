package dev.dnpm.etl.processor.services

import ca.uhn.fhir.context.FhirContext
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import dev.dnpm.etl.processor.config.GIcsConfigProperties
import dev.dnpm.etl.processor.consent.ConsentDomain
import dev.dnpm.etl.processor.consent.ICheckConsent
import dev.dnpm.etl.processor.pseudonym.ensureMetaDataIsInitialized
import dev.pcvolkmer.mv64e.mtb.*
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Consent
import org.hl7.fhir.r4.model.Consent.ProvisionComponent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.util.*

@Service
class ConsentProcessor(
    private val gIcsConfigProperties: GIcsConfigProperties, private val objectMapper: ObjectMapper,
    private val fhirContext: FhirContext,
    private val consentService: ICheckConsent?
) {
    private var logger: Logger = LoggerFactory.getLogger("ConsentProcessor")

    /**
     * In case an instance of {@link  ICheckConsent} is active, consent will be embedded and checked.
     *
     * Logik:
     *  * <c>true</c> IF consent check is disabled.
     *  * <c>true</c> IF broad consent (BC) has been given.
     *  * <c>true</c> BC has been asked AND declined but genomDe consent has been consented.
     *  * ELSE <c>false</c> is returned.
     *
     * @param mtbFile File v2 (will be enriched with consent data)
     * @return true if consent is given
     *
     */
    fun consentGatedCheckAndTryEmbedding(mtbFile: Mtb): Boolean {
        if (consentService == null) {
            // consent check seems to be disabled
            return true
        }

        mtbFile.ensureMetaDataIsInitialized()

        val personIdentifierValue = mtbFile.patient.id
        val requestDate = Date.from(Instant.now(Clock.systemUTC()))

        // 1. Broad consent Entry exists?
        // 1.1. -> yes and research consent is given -> send mtb file
        // 1.2. -> no -> return status error - consent has not been asked
        // 2. ->  Broad consent found but rejected -> is GenomDe consent provision 'sequencing' given?
        // 2.1 -> yes -> send mtb file
        // 2.2 -> no ->  warn/info no consent given

        /*
         * broad consent
         */
        val broadConsent = consentService.getBroadConsent(personIdentifierValue, requestDate)
        val broadConsentHasBeenAsked = !broadConsent.entry.isEmpty()

        // fast exit - if patient has not been asked, we can skip and exit
        if (!broadConsentHasBeenAsked) return false

        val genomeDeConsent = consentService.getGenomDeConsent(
            personIdentifierValue, requestDate
        )

        addGenomeDbProvisions(mtbFile, genomeDeConsent)


        if (!genomeDeConsent.entry.isEmpty()) setGenomDeSubmissionType(mtbFile)

        embedBroadConsentResources(mtbFile, broadConsent)

        val broadConsentStatus = consentService.getProvisionTypeByPolicyCode(
            broadConsent, requestDate, ConsentDomain.BroadConsent
        )

        val genomDeSequencingStatus = consentService.getProvisionTypeByPolicyCode(
            genomeDeConsent, requestDate, ConsentDomain.Modelvorhaben64e
        )

        if (Consent.ConsentProvisionType.NULL == broadConsentStatus) {
            // bc not asked
            return false
        }
        if (Consent.ConsentProvisionType.PERMIT == broadConsentStatus ||
            Consent.ConsentProvisionType.PERMIT == genomDeSequencingStatus
        ) return true

        return false
    }

    public fun embedBroadConsentResources(mtbFile: Mtb, broadConsent: Bundle) {
        for (entry in broadConsent.getEntry()) {
            val resource = entry.getResource()
            if (resource is Consent) {
                // since jackson convertValue does not work here,
                // we need another step to back to string, before we convert to object map
                val asJsonString =
                    fhirContext.newJsonParser().encodeResourceToString(resource)
                try {
                    val mapOfJson: HashMap<String?, Any?>? =
                        objectMapper.readValue<HashMap<String?, Any?>?>(
                            asJsonString,
                            object : TypeReference<HashMap<String?, Any?>?>() {
                            })
                    mtbFile.metadata.researchConsents.add(mapOfJson)
                } catch (e: JsonProcessingException) {
                    throw RuntimeException(e)
                }
            }
        }
    }

    public fun addGenomeDbProvisions(mtbFile: Mtb, consentGnomeDe: Bundle) {
        for (entry in consentGnomeDe.getEntry()) {
            val resource = entry.getResource()
            if (resource !is Consent) {
                continue
            }

            // We expect only one provision in collection, therefore get first or none
            val provisions = resource.getProvision().getProvision()
            if (provisions.isEmpty()) {
                continue
            }

            val provisionComponent: ProvisionComponent = provisions.first()

            var provisionCode: String? = null
            if (provisionComponent.getCode() != null && !provisionComponent.getCode().isEmpty()) {
                val codableConcept: CodeableConcept = provisionComponent.getCode().first()
                if (codableConcept.getCoding() != null && !codableConcept.getCoding().isEmpty()) {
                    provisionCode = codableConcept.getCoding().first().getCode()
                }
            }

            if (provisionCode != null) {
                try {
                    val modelProjectConsentPurpose =
                        ModelProjectConsentPurpose.forValue(provisionCode)

                    if (ModelProjectConsentPurpose.SEQUENCING == modelProjectConsentPurpose) {
                        // CONVENTION: wrapping date is date of SEQUENCING consent
                        mtbFile.metadata.modelProjectConsent.date = resource.getDateTime()
                    }

                    val provision = Provision.builder()
                        .type(ConsentProvision.valueOf(provisionComponent.getType().name))
                        .date(provisionComponent.getPeriod().getStart())
                        .purpose(modelProjectConsentPurpose)
                        .build()

                    mtbFile.metadata.modelProjectConsent.provisions.add(provision)
                } catch (ioe: IOException) {
                    logger.error(
                        "Provision code '$provisionCode' is unknown and cannot be mapped.",
                        ioe.toString()
                    )
                }
            }

            if (!mtbFile.metadata.modelProjectConsent.provisions.isEmpty()) {
                mtbFile.metadata.modelProjectConsent.version =
                    gIcsConfigProperties.genomeDeConsentVersion
            }
        }
    }

    /**
     *  fixme: currently we do not have information about submission type
     */
    private fun setGenomDeSubmissionType(mtbFile: Mtb) {
        mtbFile.metadata.type = MvhSubmissionType.INITIAL
    }

}