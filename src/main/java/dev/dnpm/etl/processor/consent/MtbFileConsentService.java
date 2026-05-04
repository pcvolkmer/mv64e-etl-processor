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
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MtbFileConsentService implements IConsentService {

  private static final Logger log = LoggerFactory.getLogger(MtbFileConsentService.class);

  public MtbFileConsentService() {
    log.info("ConsentCheckFileBased initialized...");
  }

  @Override
  @NonNull
  public TtpConsentStatus getTtpBroadConsentStatus(@NonNull String personIdentifierValue) {
    return TtpConsentStatus.UNKNOWN_CHECK_FILE;
  }

  /**
   * EMPTY METHOD: NOT IMPLEMENTED
   *
   * @return empty bundle
   */
  @Override
  @NonNull
  public Bundle getConsent(
      @NonNull String personIdentifierValue,
      @NonNull Date requestDate,
      @NonNull ConsentDomain consentDomain) {
    return new Bundle();
  }
}
