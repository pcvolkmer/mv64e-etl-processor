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

package dev.dnpm.etl.processor.pseudonym

import com.fasterxml.jackson.databind.ObjectMapper
import de.ukw.ccc.bwhc.dto.MtbFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import org.springframework.core.io.ClassPathResource

const val FAKE_MTB_FILE_PATH = "fake_MTBFile.json"
const val CLEAN_PATIENT_ID = "5dad2f0b-49c6-47d8-a952-7b9e9e0f7549"

@ExtendWith(MockitoExtension::class)
class ExtensionsTest {

    private fun fakeMtbFile(): MtbFile {
        val mtbFile = ClassPathResource(FAKE_MTB_FILE_PATH).inputStream
        return ObjectMapper().readValue(mtbFile, MtbFile::class.java)
    }

    private fun MtbFile.serialized(): String {
        return ObjectMapper().writeValueAsString(this)
    }

    @Test
    fun shouldNotContainCleanPatientId(@Mock pseudonymizeService: PseudonymizeService) {
        doAnswer {
            it.arguments[0]
            "PSEUDO-ID"
        }.whenever(pseudonymizeService).patientPseudonym(ArgumentMatchers.anyString())

        val mtbFile = fakeMtbFile()

        mtbFile.pseudonymizeWith(pseudonymizeService)

        assertThat(mtbFile.patient.id).isEqualTo("PSEUDO-ID")
        assertThat(mtbFile.serialized()).doesNotContain(CLEAN_PATIENT_ID)
    }

}