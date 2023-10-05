package dev.dnpm.etl.processor.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.PathNotFoundException
import de.ukw.ccc.bwhc.dto.MtbFile

class TransformationService(private val objectMapper: ObjectMapper) {
    fun transform(mtbFile: MtbFile, vararg transformations: Transformation): MtbFile {
        var json = objectMapper.writeValueAsString(mtbFile)

        transformations.forEach { transformation ->
            val jsonPath = JsonPath.parse(json)

            try {
                val before = transformation.path.substringBeforeLast(".")
                val last = transformation.path.substringAfterLast(".")

                val existingValue = if (transformation.existingValue is Number) transformation.existingValue else transformation.existingValue.toString()
                val newValue = if (transformation.newValue is Number) transformation.newValue else transformation.newValue.toString()

                jsonPath.set("$.$before.[?]$last", newValue, {
                    it.item(HashMap::class.java)[last] == existingValue
                })
            } catch (e: PathNotFoundException) {
                // Ignore
            }

            json = jsonPath.jsonString()
        }

        return objectMapper.readValue(json, MtbFile::class.java)
    }

}

class Transformation private constructor(internal val path: String) {

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

        fun of(path: String): Transformation {
            return Transformation(path)
        }

    }

}
