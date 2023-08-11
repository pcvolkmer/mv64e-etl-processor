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

import dev.dnpm.etl.processor.AbstractTestcontainerTest
import dev.dnpm.etl.processor.monitoring.Request
import dev.dnpm.etl.processor.monitoring.RequestRepository
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.dnpm.etl.processor.monitoring.RequestType
import dev.dnpm.etl.processor.output.MtbFileSender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.*

@Testcontainers
@ExtendWith(SpringExtension::class)
@SpringBootTest
@Transactional
@MockBean(MtbFileSender::class)
class RequestServiceIntegrationTest : AbstractTestcontainerTest() {

    private lateinit var requestRepository: RequestRepository

    private lateinit var requestService: RequestService

    @BeforeEach
    fun setup(
        @Autowired requestRepository: RequestRepository
    ) {
        this.requestRepository = requestRepository
        this.requestService = RequestService(requestRepository)
    }

    @Test
    fun shouldResultInEmptyRequestList() {
        val actual = requestService.allRequestsByPatientPseudonym("TEST_12345678901")

        assertThat(actual).isEmpty()
    }

    private fun setupTestData() {
        // Prepare DB
        this.requestRepository.saveAll(
            listOf(
                Request(
                    uuid = UUID.randomUUID().toString(),
                    patientId = "TEST_12345678901",
                    pid = "P1",
                    fingerprint = "0123456789abcdef1",
                    type = RequestType.MTB_FILE,
                    status = RequestStatus.SUCCESS,
                    processedAt = Instant.parse("2023-07-07T02:00:00Z")
                ),
                // Should be ignored - wrong patient ID -->
                Request(
                    uuid = UUID.randomUUID().toString(),
                    patientId = "TEST_12345678902",
                    pid = "P2",
                    fingerprint = "0123456789abcdef2",
                    type = RequestType.MTB_FILE,
                    status = RequestStatus.WARNING,
                    processedAt = Instant.parse("2023-08-08T00:00:00Z")
                ),
                // <--
                Request(
                    uuid = UUID.randomUUID().toString(),
                    patientId = "TEST_12345678901",
                    pid = "P2",
                    fingerprint = "0123456789abcdee1",
                    type = RequestType.DELETE,
                    status = RequestStatus.SUCCESS,
                    processedAt = Instant.parse("2023-08-08T02:00:00Z")
                )
            )
        )
    }

    @Test
    fun shouldResultInSortedRequestList() {
        setupTestData()

        val actual = requestService.allRequestsByPatientPseudonym("TEST_12345678901")

        assertThat(actual).hasSize(2)
        assertThat(actual[0].fingerprint).isEqualTo("0123456789abcdee1")
        assertThat(actual[1].fingerprint).isEqualTo("0123456789abcdef1")
    }

    @Test
    fun shouldReturnDeleteRequestAsLastRequest() {
        setupTestData()

        val actual = requestService.isLastRequestWithKnownStatusDeletion("TEST_12345678901")

        assertThat(actual).isTrue()
    }

    @Test
    fun shouldReturnLastMtbFileRequest() {
        setupTestData()

        val actual = requestService.lastMtbFileRequestForPatientPseudonym("TEST_12345678901")

        assertThat(actual).isNotNull
        assertThat(actual?.fingerprint).isEqualTo("0123456789abcdef1")
    }

}