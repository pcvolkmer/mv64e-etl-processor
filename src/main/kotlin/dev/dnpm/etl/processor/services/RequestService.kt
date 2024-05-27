/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2023  Comprehensive Cancer Center Mainfranken, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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

package dev.dnpm.etl.processor.services

import dev.dnpm.etl.processor.RequestId
import dev.dnpm.etl.processor.monitoring.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.util.*

@Service
class RequestService(
    private val requestRepository: RequestRepository
) {

    fun save(request: Request) = requestRepository.save(request)

    fun findAll(): Iterable<Request> = requestRepository.findAll()

    fun findAll(pageable: Pageable): Page<Request> = requestRepository.findAll(pageable)

    fun findByUuid(uuid: RequestId): Optional<Request> =
        requestRepository.findByUuidEquals(uuid)

    fun findRequestByPatientId(patientId: String, pageable: Pageable): Page<Request> = requestRepository.findRequestByPatientId(patientId, pageable)

    fun allRequestsByPatientPseudonym(patientPseudonym: String) = requestRepository
        .findAllByPatientIdOrderByProcessedAtDesc(patientPseudonym)

    fun lastMtbFileRequestForPatientPseudonym(patientPseudonym: String) =
        Companion.lastMtbFileRequestForPatientPseudonym(allRequestsByPatientPseudonym(patientPseudonym))

    fun isLastRequestWithKnownStatusDeletion(patientPseudonym: String) =
        Companion.isLastRequestWithKnownStatusDeletion(allRequestsByPatientPseudonym(patientPseudonym))

    fun countStates(): Iterable<CountedState> = requestRepository.countStates()

    fun countDeleteStates(): Iterable<CountedState> = requestRepository.countDeleteStates()

    fun findPatientUniqueStates(): List<CountedState> = requestRepository.findPatientUniqueStates()

    fun findPatientUniqueDeleteStates(): List<CountedState> = requestRepository.findPatientUniqueDeleteStates()

    companion object {

        fun lastMtbFileRequestForPatientPseudonym(allRequests: List<Request>) = allRequests
            .filter { it.type == RequestType.MTB_FILE }
            .sortedByDescending { it.processedAt }
            .firstOrNull { it.status == RequestStatus.SUCCESS || it.status == RequestStatus.WARNING }

        fun isLastRequestWithKnownStatusDeletion(allRequests: List<Request>) = allRequests
            .filter { it.status != RequestStatus.UNKNOWN }
            .maxByOrNull { it.processedAt }?.type == RequestType.DELETE

    }

}