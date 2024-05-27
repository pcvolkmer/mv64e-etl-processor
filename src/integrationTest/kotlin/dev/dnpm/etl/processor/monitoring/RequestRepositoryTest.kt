/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2024  Comprehensive Cancer Center Mainfranken, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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

import dev.dnpm.etl.processor.AbstractTestcontainerTest
import dev.dnpm.etl.processor.Fingerprint
import dev.dnpm.etl.processor.output.MtbFileSender
import dev.dnpm.etl.processor.randomRequestId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

@Testcontainers
@ExtendWith(SpringExtension::class)
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@MockBean(MtbFileSender::class)
@TestPropertySource(
    properties = [
        "app.pseudonymize.generator=buildin",
        "app.rest.uri=http://example.com"
    ]
)
class RequestRepositoryTest : AbstractTestcontainerTest() {

    private lateinit var requestRepository: RequestRepository

    @BeforeEach
    fun setUp(
        @Autowired requestRepository: RequestRepository
    ) {
        this.requestRepository = requestRepository
    }

    @Test
    fun shouldSaveRequest() {
        val request = Request(
            randomRequestId(),
            "TEST_12345678901",
            "P1",
            Fingerprint("0123456789abcdef1"),
            RequestType.MTB_FILE,
            RequestStatus.WARNING,
            Instant.parse("2023-07-07T00:00:00Z")
        )

        requestRepository.save(request)
    }

}