package dev.dnpm.etl.processor.services

import ca.uhn.fhir.context.FhirContext
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import dev.dnpm.etl.processor.config.GIcsConfigProperties
import dev.dnpm.etl.processor.consent.ConsentDomain
import dev.dnpm.etl.processor.consent.IGetConsent
import dev.dnpm.etl.processor.pseudonym.ensureMetaDataIsInitialized
import dev.pcvolkmer.mv64e.mtb.*
import org.apache.commons.lang3.NotImplementedException
import org.hl7.fhir.r4.model.*
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Consent.ConsentState
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
    private val gIcsConfigProperties: GIcsConfigProperties,
    private val objectMapper: ObjectMapper,
    private val fhirContext: FhirContext,
    private val consentService: IGetConsent?
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

        val broadConsentStatus = getProvisionTypeByPolicyCode(
            broadConsent, requestDate, ConsentDomain.BroadConsent
        )

        val genomDeSequencingStatus = getProvisionTypeByPolicyCode(
            genomeDeConsent, requestDate, ConsentDomain.Modelvorhaben64e
        )

        if (Consent.ConsentProvisionType.NULL == broadConsentStatus) {
            // bc not asked
            return false
        }
        if (Consent.ConsentProvisionType.PERMIT == broadConsentStatus || Consent.ConsentProvisionType.PERMIT == genomDeSequencingStatus) return true

        return false
    }

    fun embedBroadConsentResources(mtbFile: Mtb, broadConsent: Bundle) {
        for (entry in broadConsent.getEntry()) {
            val resource = entry.getResource()
            if (resource is Consent) {
                // since jackson convertValue does not work here,
                // we need another step to back to string, before we convert to object map
                val asJsonString = fhirContext.newJsonParser().encodeResourceToString(resource)
                try {
                    val mapOfJson: HashMap<String?, Any?>? =
                        objectMapper.readValue<HashMap<String?, Any?>?>(
                            asJsonString, object : TypeReference<HashMap<String?, Any?>?>() {})
                    mtbFile.metadata.researchConsents.add(mapOfJson)
                } catch (e: JsonProcessingException) {
                    throw RuntimeException(e)
                }
            }
        }
    }

    fun addGenomeDbProvisions(mtbFile: Mtb, consentGnomeDe: Bundle) {
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
                        .purpose(modelProjectConsentPurpose).build()

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

    /**
     * @param consentBundle consent resource
     * @param requestDate   date which must be within validation period of provision
     * @return type of provision, will be [org.hl7.fhir.r4.model.Consent.ConsentProvisionType.NULL] if none is found.
     */
    fun getProvisionTypeByPolicyCode(
        consentBundle: Bundle, requestDate: Date?, consentDomain: ConsentDomain
    ): Consent.ConsentProvisionType {
        val code: String?
        val system: String?
        if (ConsentDomain.BroadConsent == consentDomain) {
            code = gIcsConfigProperties.broadConsentPolicyCode
            system = gIcsConfigProperties.broadConsentPolicySystem
        } else if (ConsentDomain.Modelvorhaben64e == consentDomain) {
            code = gIcsConfigProperties.genomeDePolicyCode
            system = gIcsConfigProperties.genomeDePolicySystem
        } else {
            throw NotImplementedException("unknown consent domain " + consentDomain.name)
        }

        val provisionTypeByPolicyCode = getProvisionTypeByPolicyCode(
            consentBundle, code, system, requestDate
        )
        return provisionTypeByPolicyCode
    }

    /**
     * @param consentBundle            consent resource
     * @param policyAndProvisionCode   policyRule and provision code value
     * @param policyAndProvisionSystem policyRule and provision system value
     * @param requestDate              date which must be within validation period of provision
     * @return type of provision, will be [org.hl7.fhir.r4.model.Consent.ConsentProvisionType.NULL] if none is found.
     */
    fun getProvisionTypeByPolicyCode(
        consentBundle: Bundle,
        policyAndProvisionCode: String?,
        policyAndProvisionSystem: String?,
        requestDate: Date?
    ): Consent.ConsentProvisionType {
        val entriesOfInterest = consentBundle.entry.filter { entry ->
            entry.resource.isResource && entry.resource.resourceType == ResourceType.Consent && (entry.resource as Consent).status == ConsentState.ACTIVE && checkCoding(
                policyAndProvisionCode,
                policyAndProvisionSystem,
                (entry.resource as Consent).policyRule.codingFirstRep
            ) && isIsRequestDateInRange(
                requestDate, (entry.resource as Consent).provision.period
            )
        }.map { consentWithTargetPolicy: BundleEntryComponent ->
            val provision = (consentWithTargetPolicy.getResource() as Consent).getProvision()
            val provisionComponentByCode =
                provision.getProvision().stream().filter { prov: ProvisionComponent? ->
                    checkCoding(
                        policyAndProvisionCode,
                        policyAndProvisionSystem,
                        prov!!.getCodeFirstRep().getCodingFirstRep()
                    ) && isIsRequestDateInRange(
                        requestDate, prov.getPeriod()
                    )
                }.findFirst()
            if (provisionComponentByCode.isPresent) {
                // actual provision we search for
                return@map provisionComponentByCode.get().getType()
            } else {
                if (provision.type != null) return provision.type

            }
            return Consent.ConsentProvisionType.NULL
        }.firstOrNull()

        if (entriesOfInterest == null) return Consent.ConsentProvisionType.NULL
        return entriesOfInterest
    }

    fun checkCoding(
        researchAllowedPolicyOid: String?, researchAllowedPolicySystem: String?, coding: Coding
    ): Boolean {
        return coding.getSystem() == researchAllowedPolicySystem && (coding.getCode() == researchAllowedPolicyOid)
    }

    fun isIsRequestDateInRange(requestdate: Date?, provPeriod: Period): Boolean {
        val isRequestDateAfterOrEqualStart = provPeriod.getStart().compareTo(requestdate)
        val isRequestDateBeforeOrEqualEnd = provPeriod.getEnd().compareTo(requestdate)
        return isRequestDateAfterOrEqualStart <= 0 && isRequestDateBeforeOrEqualEnd >= 0
    }

}