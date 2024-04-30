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

import dev.dnpm.etl.processor.Fingerprint
import dev.dnpm.etl.processor.monitoring.Request
import dev.dnpm.etl.processor.monitoring.RequestRepository
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.dnpm.etl.processor.monitoring.RequestType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Instant
import java.util.*

@ExtendWith(MockitoExtension::class)
class RequestServiceTest {

    private lateinit var requestRepository: RequestRepository

    private lateinit var requestService: RequestService

    private fun anyRequest() = any(Request::class.java) ?: Request(
        0L,
        UUID.randomUUID().toString(),
        "TEST_dummy",
        "PX",
        Fingerprint("dummy"),
        RequestType.MTB_FILE,
        RequestStatus.SUCCESS,
        Instant.parse("2023-08-08T02:00:00Z")
    )

    @BeforeEach
    fun setup(
        @Mock requestRepository: RequestRepository
    ) {
        this.requestRepository = requestRepository
        this.requestService = RequestService(requestRepository)
    }

    @Test
    fun shouldIndicateLastRequestIsDeleteRequest() {
        val requests = listOf(
            Request(
                1L,
                UUID.randomUUID().toString(),
                "TEST_12345678901",
                "P1",
                Fingerprint("0123456789abcdef1"),
                RequestType.MTB_FILE,
                RequestStatus.WARNING,
                Instant.parse("2023-07-07T00:00:00Z")
            ),
            Request(
                2L,
                UUID.randomUUID().toString(),
                "TEST_12345678901",
                "P1",
                Fingerprint("0123456789abcdefd"),
                RequestType.DELETE,
                RequestStatus.WARNING,
                Instant.parse("2023-07-07T02:00:00Z")
            ),
            Request(
                3L,
                UUID.randomUUID().toString(),
                "TEST_12345678901",
                "P1",
                Fingerprint("0123456789abcdef1"),
                RequestType.MTB_FILE,
                RequestStatus.UNKNOWN,
                Instant.parse("2023-08-11T00:00:00Z")
            )
        )

        val actual = RequestService.isLastRequestWithKnownStatusDeletion(requests)

        assertThat(actual).isTrue()
    }

    @Test
    fun shouldIndicateLastRequestIsNotDeleteRequest() {
        val requests = listOf(
            Request(
                1L,
                UUID.randomUUID().toString(),
                "TEST_12345678901",
                "P1",
                Fingerprint("0123456789abcdef1"),
                RequestType.MTB_FILE,
                RequestStatus.WARNING,
                Instant.parse("2023-07-07T00:00:00Z")
            ),
            Request(
                2L,
                UUID.randomUUID().toString(),
                "TEST_12345678901",
                "P1",
                Fingerprint("0123456789abcdef1"),
                RequestType.MTB_FILE,
                RequestStatus.WARNING,
                Instant.parse("2023-07-07T02:00:00Z")
            ),
            Request(
                3L,
                UUID.randomUUID().toString(),
                "TEST_12345678901",
                "P1",
                Fingerprint("0123456789abcdef1"),
                RequestType.MTB_FILE,
                RequestStatus.UNKNOWN,
                Instant.parse("2023-08-11T00:00:00Z")
            )
        )

        val actual = RequestService.isLastRequestWithKnownStatusDeletion(requests)

        assertThat(actual).isFalse()
    }

    @Test
    fun shouldReturnPatientsLastRequest() {
        val requests = listOf(
            Request(
                1L,
                UUID.randomUUID().toString(),
                "TEST_12345678901",
                "P1",
                Fingerprint("0123456789abcdef1"),
                RequestType.DELETE,
                RequestStatus.SUCCESS,
                Instant.parse("2023-07-07T02:00:00Z")
            ),
            Request(
                1L,
                UUID.randomUUID().toString(),
                "TEST_12345678902",
                "P2",
                Fingerprint("0123456789abcdef2"),
                RequestType.MTB_FILE,
                RequestStatus.WARNING,
                Instant.parse("2023-08-08T00:00:00Z")
            )
        )

        val actual = RequestService.lastMtbFileRequestForPatientPseudonym(requests)

        assertThat(actual).isInstanceOf(Request::class.java)
        assertThat(actual?.fingerprint).isEqualTo(Fingerprint("0123456789abcdef2"))
    }

    @Test
    fun shouldReturnNullIfNoRequests() {
        val requests = listOf<Request>()

        val actual = RequestService.lastMtbFileRequestForPatientPseudonym(requests)

        assertThat(actual).isNull()
    }

    @Test
    fun saveShouldSaveRequestUsingRepository() {
        doAnswer {
            val obj = it.arguments[0] as Request
            obj.copy(id = 1L)
        }.`when`(requestRepository).save(anyRequest())

        val request = Request(
            UUID.randomUUID().toString(),
            "TEST_12345678901",
            "P1",
            Fingerprint("0123456789abcdef1"),
            RequestType.DELETE,
            RequestStatus.SUCCESS,
            Instant.parse("2023-07-07T02:00:00Z")
        )

        requestService.save(request)

        verify(requestRepository, times(1)).save(anyRequest())
    }

    @Test
    fun allRequestsByPatientPseudonymShouldRequestAllRequestsForPatientPseudonym() {
        requestService.allRequestsByPatientPseudonym("TEST_12345678901")

        verify(requestRepository, times(1)).findAllByPatientIdOrderByProcessedAtDesc(anyString())
    }

    @Test
    fun lastMtbFileRequestForPatientPseudonymShouldRequestAllRequestsForPatientPseudonym() {
        requestService.lastMtbFileRequestForPatientPseudonym("TEST_12345678901")

        verify(requestRepository, times(1)).findAllByPatientIdOrderByProcessedAtDesc(anyString())
    }

    @Test
    fun isLastRequestDeletionShouldRequestAllRequestsForPatientPseudonym() {
        requestService.isLastRequestWithKnownStatusDeletion("TEST_12345678901")

        verify(requestRepository, times(1)).findAllByPatientIdOrderByProcessedAtDesc(anyString())
    }

}