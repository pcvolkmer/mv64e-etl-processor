package dev.dnpm.etl.processor.pseudonym;

public class PseudonymRequestFailed extends RuntimeException {

  public PseudonymRequestFailed(String message) {
    super(message);
  }

  public PseudonymRequestFailed(String message, Throwable cause) {
    super(message, cause);
  }
}
