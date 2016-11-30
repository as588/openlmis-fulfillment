package org.openlmis.fulfillment.util;

/**
 * Signals user being unauthorized in external api.
 */
public class AuthenticationException extends RuntimeException {
  public AuthenticationException(String message) {
    super(message);
  }
}