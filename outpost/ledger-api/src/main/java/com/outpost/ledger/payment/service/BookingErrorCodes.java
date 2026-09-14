package com.outpost.ledger.payment.service;

/** Why an accounting request is not booked. */
public enum BookingErrorCodes {
  INVALID_REQUEST,
  UNKNOWN_ACCOUNT,
  MISSING_ACCOUNT,
  INCONSISTENT_AMOUNTS,
  MISSING_FEE_CONFIGURATION,
  FEE_ABOVE_NET,
  PAYMENT_NOT_FOUND,
  REFERENCE_CONFLICT,
  INVALID_TRANSITION,
  CAPTURE_CONFLICT,
  INVALID_CAPTURE,
  NOT_CAPTURED,
  /** The payment's refunds would exceed its captured net, tax, or gross. */
  REFUND_EXCEEDS_CAPTURE,
  /** Stored accounting data does not fit the booking; the cause names what disagreed. */
  INCONSISTENT_BOOKING
}
