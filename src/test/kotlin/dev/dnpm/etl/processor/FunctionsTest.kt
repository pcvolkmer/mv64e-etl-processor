/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2023       Comprehensive Cancer Center Mainfranken
 * Copyright (c) 2026  Paul-Christian Volkmer, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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

package dev.dnpm.etl.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.util.stream.Stream

class FunctionsTest {
    @ParameterizedTest
    @MethodSource("listToPageTestData")
    fun shouldConvertListToPage(
        list: List<Int>,
        pageable: Pageable,
        expected: List<Int>,
    ) {
        val actual = list.toPage(pageable)
        assertThat(actual.content).containsExactlyElementsOf(expected)
        assertThat(actual.totalElements).isEqualTo(list.size.toLong())
    }

    companion object {
        @JvmStatic
        fun listToPageTestData(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
                    PageRequest.of(0, 5),
                    listOf(1, 2, 3, 4, 5),
                ),
                Arguments.of(
                    listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
                    PageRequest.of(1, 5),
                    listOf(6, 7, 8, 9, 10),
                ),
                Arguments.of(
                    listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
                    PageRequest.of(2, 5),
                    emptyList<Int>(),
                ),
                Arguments.of(
                    emptyList<Int>(),
                    PageRequest.of(1, 5),
                    emptyList<Int>(),
                ),
            )
    }
}
