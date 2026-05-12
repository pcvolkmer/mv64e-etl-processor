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

package dev.dnpm.etl.processor.consent;

public enum TtpConsentStatus {
  /** Valid consent found */
  BROAD_CONSENT_GIVEN,
  /** Missing or rejected...actually unknown */
  BROAD_CONSENT_MISSING_OR_REJECTED,
  /** No Broad consent policy found */
  BROAD_CONSENT_MISSING,
  /** Research policy has been rejected */
  BROAD_CONSENT_REJECTED,

  GENOM_DE_CONSENT_SEQUENCING_PERMIT,
  /** No GenomDE consent policy found */
  GENOM_DE_CONSENT_MISSING,
  /** GenomDE consent policy found, but has been rejected */
  GENOM_DE_SEQUENCING_REJECTED,
  /** Consent status is validate via file property 'consent.status' */
  UNKNOWN_CHECK_FILE,
  /** Due technical problems consent status is unknown */
  FAILED_TO_ASK
}
