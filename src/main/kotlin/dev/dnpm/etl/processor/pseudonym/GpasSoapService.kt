package dev.dnpm.etl.processor.pseudonym

import jakarta.jws.WebMethod
import jakarta.jws.WebParam
import jakarta.jws.WebResult
import jakarta.jws.WebService

@WebService(
    name = "PSNManagerBeanService",
    targetNamespace ="http://psn.ttp.ganimed.icmvc.emau.org/"
)
interface GpasSoapService {

    @WebMethod(operationName = "getOrCreatePseudonymFor")
    @WebResult(name = "psn")
    fun getOrCreatePseudonymFor(
        @WebParam(name = "value") value: String,
        @WebParam(name = "domainName") domainName: String
    ): String

}