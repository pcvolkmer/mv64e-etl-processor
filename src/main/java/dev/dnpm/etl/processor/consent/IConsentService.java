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

import java.util.Date;
import org.hl7.fhir.r4.model.Bundle;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface IConsentService {

  /**
   * Get broad consent status for a patient identifier
   *
   * @param personIdentifierValue patient identifier used for consent data
   * @return status of broad consent
   * @apiNote cannot not differ between not asked and rejected
   */
  TtpConsentStatus getTtpBroadConsentStatus(String personIdentifierValue);

  /**
   * Get broad consent policies with respect to a request date
   *
   * @param personIdentifierValue patient identifier used for consent data
   * @param requestDate target date until consent data should be considered
   * @return consent policies as bundle;
   *     <p>if empty patient has not been asked, yet.
   */
  Bundle getConsent(String personIdentifierValue, Date requestDate, ConsentDomain consentDomain);
}
