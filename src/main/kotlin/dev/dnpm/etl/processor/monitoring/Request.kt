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

package dev.dnpm.etl.processor.monitoring

import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Embedded
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import java.time.Instant
import java.util.*

typealias RequestId = UUID

@Table("request")
data class Request(
    @Id val id: Long? = null,
    val uuid: String = RequestId.randomUUID().toString(),
    val patientId: String,
    val pid: String,
    val fingerprint: String,
    val status: RequestStatus,
    val type: RequestType,
    val processedAt: Instant = Instant.now(),
    @Embedded.Nullable var report: Report? = null
)

data class Report(
    val description: String,
    val dataQualityReport: String = ""
)

data class CountedState(
    val count: Int,
    val status: RequestStatus,
)

interface RequestRepository : CrudRepository<Request, Long> {

    fun findAllByPatientIdOrderByProcessedAtDesc(patientId: String): List<Request>

    fun findByUuidEquals(uuid: String): Optional<Request>

    @Query("SELECT count(*) AS count, status FROM request WHERE type = 'MTB_FILE' GROUP BY status ORDER BY status, count DESC;")
    fun countStates(): List<CountedState>

    @Query("SELECT count(*) AS count, status FROM (" +
            "SELECT status, rank() OVER (PARTITION BY patient_id ORDER BY processed_at DESC) AS rank FROM request " +
            "WHERE type = 'MTB_FILE' AND status NOT IN ('DUPLICATION') " +
            ") rank WHERE rank = 1 GROUP BY status ORDER BY status, count DESC;")
    fun findPatientUniqueStates(): List<CountedState>

    @Query("SELECT count(*) AS count, status FROM request WHERE type = 'DELETE' GROUP BY status ORDER BY status, count DESC;")
    fun countDeleteStates(): List<CountedState>

    @Query("SELECT count(*) AS count, status FROM (" +
            "SELECT status, rank() OVER (PARTITION BY patient_id ORDER BY processed_at DESC) AS rank FROM request " +
            "WHERE type = 'DELETE'" +
            ") rank WHERE rank = 1 GROUP BY status ORDER BY status, count DESC;")
    fun findPatientUniqueDeleteStates(): List<CountedState>

}