package dev.dnpm.etl.processor.services

import ca.uhn.fhir.context.FhirContext
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import dev.dnpm.etl.processor.config.AppConfigProperties
import dev.dnpm.etl.processor.config.GIcsConfigProperties
import dev.dnpm.etl.processor.consent.ConsentDomain
import dev.dnpm.etl.processor.consent.IConsentService
import dev.dnpm.etl.processor.consent.MtbFileConsentService
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
    private val appConfigProperties: AppConfigProperties,
    private val gIcsConfigProperties: GIcsConfigProperties,
    private val objectMapper: ObjectMapper,
    private val fhirContext: FhirContext,
    private val consentService: IConsentService
) {
    private var logger: Logger = LoggerFactory.getLogger("ConsentProcessor")

    /**
     * In case an instance of {@link  ICheckConsent} is active, consent will be embedded and checked.
     *
     * Logic:
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
        if (consentService is MtbFileConsentService) {
            // consent check is disabled
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
        val broadConsent = consentService.getConsent(
            personIdentifierValue, requestDate, ConsentDomain.BROAD_CONSENT
        )
        val broadConsentHasBeenAsked = broadConsent.entry.isNotEmpty()

        // fast exit - if patient has not been asked, we can skip and exit
        if (!broadConsentHasBeenAsked) return false

        val genomeDeConsent = consentService.getConsent(
            personIdentifierValue, requestDate, ConsentDomain.MODELLVORHABEN_64E
        )

        addGenomeDbProvisions(mtbFile, genomeDeConsent)

        if (genomeDeConsent.entry.isNotEmpty()) setGenomDeSubmissionType(mtbFile)

        embedBroadConsentResources(mtbFile, broadConsent)

        val broadConsentStatus = getProvisionTypeByPolicyCode(
            broadConsent, requestDate, ConsentDomain.BROAD_CONSENT
        )

        val genomDeSequencingStatus = getProvisionTypeByPolicyCode(
            genomeDeConsent, requestDate, ConsentDomain.MODELLVORHABEN_64E
        )

        if (Consent.ConsentProvisionType.NULL == broadConsentStatus) {
            // bc not asked
            return false
        }
        if (Consent.ConsentProvisionType.PERMIT == broadConsentStatus || Consent.ConsentProvisionType.PERMIT == genomDeSequencingStatus) return true

        return false
    }

    fun embedBroadConsentResources(mtbFile: Mtb, broadConsent: Bundle) {
        for (entry in broadConsent.entry) {
            val resource = entry.resource
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
        for (entry in consentGnomeDe.entry) {
            val resource = entry.resource
            if (resource !is Consent) {
                continue
            }

            // We expect only one provision in collection, therefore get first or none
            val provisions = resource.provision.provision
            if (provisions.isEmpty()) {
                continue
            }

            val provisionComponent: ProvisionComponent = provisions.first()

            var provisionCode: String? = null
            if (provisionComponent.code != null && provisionComponent.code.isNotEmpty()) {
                val codableConcept: CodeableConcept = provisionComponent.code.first()
                if (codableConcept.coding != null && codableConcept.coding.isNotEmpty()) {
                    provisionCode = codableConcept.coding.first().code
                }
            }

            if (provisionCode != null) {
                try {
                    val modelProjectConsentPurpose =
                        ModelProjectConsentPurpose.forValue(provisionCode)

                    if (ModelProjectConsentPurpose.SEQUENCING == modelProjectConsentPurpose) {
                        // CONVENTION: wrapping date is date of SEQUENCING consent
                        mtbFile.metadata.modelProjectConsent.date = resource.dateTime
                    }

                    val provision = Provision.builder()
                        .type(ConsentProvision.valueOf(provisionComponent.type.name))
                        .date(provisionComponent.period.start)
                        .purpose(modelProjectConsentPurpose).build()

                    mtbFile.metadata.modelProjectConsent.provisions.add(provision)
                } catch (ioe: IOException) {
                    logger.error(
                        "Provision code '$provisionCode' is unknown and cannot be mapped.",
                        ioe.toString()
                    )
                }
            }

            if (mtbFile.metadata.modelProjectConsent.provisions.isNotEmpty()) {
                mtbFile.metadata.modelProjectConsent.version =
                    gIcsConfigProperties.genomeDeConsentVersion
            }
        }
    }

    private fun setGenomDeSubmissionType(mtbFile: Mtb) {
        if (appConfigProperties.genomDeTestSubmission) {
            mtbFile.metadata.type = MvhSubmissionType.TEST
            logger.info("genomeDe submission mit TEST")
        } else {
            mtbFile.metadata.type = when (mtbFile.metadata.type) {
                null -> MvhSubmissionType.INITIAL
                else -> mtbFile.metadata.type
            }
        }
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
        if (ConsentDomain.BROAD_CONSENT == consentDomain) {
            code = gIcsConfigProperties.broadConsentPolicyCode
            system = gIcsConfigProperties.broadConsentPolicySystem
        } else if (ConsentDomain.MODELLVORHABEN_64E == consentDomain) {
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
     * @param targetCode   policyRule and provision code value
     * @param targetSystem policyRule and provision system value
     * @param requestDate              date which must be within validation period of provision
     * @return type of provision, will be [org.hl7.fhir.r4.model.Consent.ConsentProvisionType.NULL] if none is found.
     */
    fun getProvisionTypeByPolicyCode(
        consentBundle: Bundle, targetCode: String?, targetSystem: String?, requestDate: Date?
    ): Consent.ConsentProvisionType {
        val entriesOfInterest = consentBundle.entry.filter { entry ->
            val isConsentResource =
                entry.resource.isResource && entry.resource.resourceType == ResourceType.Consent
            val consentIsActive = (entry.resource as Consent).status == ConsentState.ACTIVE

            isConsentResource && consentIsActive && checkCoding(
                targetCode, targetSystem, (entry.resource as Consent).policyRule.coding
            ) && isRequestDateInRange(requestDate, (entry.resource as Consent).provision.period)
        }.map { entry: BundleEntryComponent ->
            val consent = (entry.getResource() as Consent)
            consent.provision.provision.filter { subProvision ->
                isRequestDateInRange(requestDate, subProvision.period)
                // search coding entries of current provision for code and system
                subProvision.code.map { c -> c.coding }.flatten().firstOrNull { code ->
                    targetCode.equals(code.code) && targetSystem.equals(code.system)
                } != null
            }.map { subProvision ->
                subProvision
            }
        }.flatten()

        if (entriesOfInterest.isNotEmpty()) {
            return entriesOfInterest.first().type
        }
        return Consent.ConsentProvisionType.NULL
    }

    fun checkCoding(
        researchAllowedPolicyOid: String?,
        researchAllowedPolicySystem: String?,
        policyRules: Collection<Coding>
    ): Boolean {
        return policyRules.find { code ->
            researchAllowedPolicySystem.equals(code.getSystem()) && (researchAllowedPolicyOid.equals(
                code.getCode()
            ))
        } != null
    }

    fun isRequestDateInRange(requestDate: Date?, provPeriod: Period): Boolean {
        val isRequestDateAfterOrEqualStart = provPeriod.start.compareTo(requestDate)
        val isRequestDateBeforeOrEqualEnd = provPeriod.end.compareTo(requestDate)
        return isRequestDateAfterOrEqualStart <= 0 && isRequestDateBeforeOrEqualEnd >= 0
    }

}
