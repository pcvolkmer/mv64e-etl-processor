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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import dev.dnpm.etl.processor.monitoring.Request
import dev.dnpm.etl.processor.monitoring.RequestRepository
import dev.dnpm.etl.processor.monitoring.RequestStatus
import dev.dnpm.etl.processor.monitoring.RequestType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import reactor.core.publisher.Sinks
import java.time.Instant
import java.util.*

@ExtendWith(MockitoExtension::class)
class ResponseProcessorTest {

    private lateinit var requestRepository: RequestRepository
    private lateinit var statisticsUpdateProducer: Sinks.Many<Any>

    private lateinit var responseProcessor: ResponseProcessor

    private val testRequest = Request(
        1L,
        "TestID1234",
        "PSEUDONYM-A",
        "1",
        "dummyfingerprint",
        RequestType.MTB_FILE,
        RequestStatus.UNKNOWN
    )

    @BeforeEach
    fun setup(
        @Mock requestRepository: RequestRepository,
        @Mock statisticsUpdateProducer: Sinks.Many<Any>
    ) {
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

        this.requestRepository = requestRepository
        this.statisticsUpdateProducer = statisticsUpdateProducer

        this.responseProcessor = ResponseProcessor(requestRepository, statisticsUpdateProducer, objectMapper)
    }

    @Test
    fun shouldNotSaveStatusForUnknownRequest() {
        doAnswer {
            Optional.empty<Request>()
        }.whenever(requestRepository).findByUuidEquals(anyString())

        val event = ResponseEvent(
            "TestID1234",
            Instant.parse("2023-09-09T00:00:00Z"),
            RequestStatus.SUCCESS
        )

        this.responseProcessor.handleResponseEvent(event)

        verify(requestRepository, never()).save(any())
    }

    @Test
    fun shouldNotSaveStatusWithUnknownState() {
        doAnswer {
            Optional.of(testRequest)
        }.whenever(requestRepository).findByUuidEquals(anyString())

        val event = ResponseEvent(
            "TestID1234",
            Instant.parse("2023-09-09T00:00:00Z"),
            RequestStatus.UNKNOWN
        )

        this.responseProcessor.handleResponseEvent(event)

        verify(requestRepository, never()).save(any())
    }

    @ParameterizedTest
    @MethodSource("requestStatusSource")
    fun shouldSaveStatusForKnownRequest(requestStatus: RequestStatus) {
        doAnswer {
            Optional.of(testRequest)
        }.whenever(requestRepository).findByUuidEquals(anyString())

        val event = ResponseEvent(
            "TestID1234",
            Instant.parse("2023-09-09T00:00:00Z"),
            requestStatus
        )

        this.responseProcessor.handleResponseEvent(event)

        val captor = argumentCaptor<Request>()
        verify(requestRepository, times(1)).save(captor.capture())
        assertThat(captor.firstValue).isNotNull
        assertThat(captor.firstValue.status).isEqualTo(requestStatus)
    }

    companion object {

        @JvmStatic
        fun requestStatusSource(): Set<RequestStatus> {
            return setOf(
                RequestStatus.SUCCESS,
                RequestStatus.WARNING,
                RequestStatus.ERROR,
                RequestStatus.DUPLICATION
            )
        }

    }

}