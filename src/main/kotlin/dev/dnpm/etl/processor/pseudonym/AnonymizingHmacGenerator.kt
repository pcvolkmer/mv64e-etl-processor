/*
 * This file is part of ETL-Processor
 *
 * Copyright (c) 2023       Comprehensive Cancer Center Mainfranken
 * Copyright (c) 2023-2026  Paul-Christian Volkmer
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

import dev.dnpm.etl.processor.config.AppConfigProperties
import dev.dnpm.etl.processor.config.PseudonymizeConfigProperties
import org.apache.commons.codec.binary.Base32
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.codec.digest.HmacAlgorithms
import org.apache.commons.codec.digest.HmacUtils
import java.security.SecureRandom

/**
 * Variant of AnonymizingGenerator that uses app.pseudonymize.prefix as HMAC-SHA256 key
 */
class AnonymizingHmacGenerator(
    private val pseudonymizeConfigProperties: PseudonymizeConfigProperties,
) : Generator {
    fun getSecureRandom(): SecureRandom = SecureRandom()

    override fun generate(id: String): String =
        Base32()
            .encodeAsString(HmacUtils(HmacAlgorithms.HMAC_SHA_256, pseudonymizeConfigProperties.hmacKey).hmac(id))
            .substring(0..41)
            .lowercase()

    @OptIn(ExperimentalStdlibApi::class)
    override fun generateGenomDeTan(id: String): String {
        val bytes = ByteArray(64 / 2)
        getSecureRandom().nextBytes(bytes)

        return bytes.joinToString("") { "%02x".format(it) }
    }
}
