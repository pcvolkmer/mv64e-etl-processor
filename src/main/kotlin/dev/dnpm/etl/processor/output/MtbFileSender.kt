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

package dev.dnpm.etl.processor.output

import de.ukw.ccc.bwhc.dto.MtbFile
import dev.dnpm.etl.processor.monitoring.RequestStatus
import org.springframework.http.HttpStatusCode

interface MtbFileSender {
    fun send(request: MtbFileRequest): Response

    fun send(request: DeleteRequest): Response

    data class Response(val status: RequestStatus, val body: String = "")

    data class MtbFileRequest(val requestId: String, val mtbFile: MtbFile)

    data class DeleteRequest(val requestId: String, val patientId: String)

}

fun Int.asRequestStatus(): RequestStatus {
    return when (this) {
        200 -> RequestStatus.SUCCESS
        201 -> RequestStatus.WARNING
        in 400 .. 999 ->  RequestStatus.ERROR
        else ->  RequestStatus.UNKNOWN
    }
}

fun HttpStatusCode.asRequestStatus(): RequestStatus {
    return this.value().asRequestStatus()
}