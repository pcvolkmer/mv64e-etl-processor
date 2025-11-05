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
