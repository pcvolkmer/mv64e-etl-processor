/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2023       Comprehensive Cancer Center Mainfranken
 * Copyright (c) 2023-2026  Paul-Christian Volkmer, Datenintegrationszentrum Philipps-Universität Marburg and Contributors
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

import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.PathNotFoundException
import dev.pcvolkmer.mv64e.mtb.Mtb
import tools.jackson.databind.json.JsonMapper

class TransformationService(
    private val jsonMapper: JsonMapper,
    private val transformations: List<Transformation>,
) {
    fun transform(mtbFile: Mtb): Mtb {
        val json = transform(jsonMapper.writeValueAsString(mtbFile))
        return jsonMapper.readValue(json, Mtb::class.java)
    }

    private fun transform(content: String): String {
        var json = content

        transformations.forEach { transformation ->
            val jsonPath = JsonPath.parse(json)

            try {
                val before = transformation.path.substringBeforeLast(".")
                val last = transformation.path.substringAfterLast(".")

                val existingValue =
                    if (transformation.existingValue is Number) {
                        transformation.existingValue
                    } else {
                        transformation.existingValue.toString()
                    }
                val newValue =
                    if (transformation.newValue is Number) {
                        transformation.newValue
                    } else {
                        transformation.newValue.toString()
                    }

                jsonPath.set(
                    "$.$before.[?]$last",
                    newValue,
                    { it.item(HashMap::class.java)[last] == existingValue },
                )
            } catch (_: PathNotFoundException) {
                // Ignore
            }

            json = jsonPath.jsonString()
        }

        return json
    }

    fun getTransformations(): List<Transformation> = this.transformations
}

class Transformation
    private constructor(
        val path: String,
    ) {
        lateinit var existingValue: Any
            private set

        lateinit var newValue: Any
            private set

        infix fun from(value: Any): Transformation {
            this.existingValue = value
            return this
        }

        infix fun to(value: Any): Transformation {
            this.newValue = value
            return this
        }

        companion object {
            fun of(path: String): Transformation = Transformation(path)
        }
    }
