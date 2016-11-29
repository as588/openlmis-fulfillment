package org.openlmis.fulfillment.referencedata.service;

/**
 * Signals that an operation was attempted on a requisition with a bad status,
 * i.e. approving of initiated requisition.
 */
public class InvalidOrderFacilityException extends Exception {

  public InvalidOrderFacilityException(String message) {
    super(message);
  }
}
