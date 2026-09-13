package com.outpost.gateway.psp.service;

/** Result codes of processing a PSP event notification. */
public enum PspWebhookProcessResultCodes {
  ACCEPTED,
  UNKNOWN_PSP,
  INVALID_SIGNATURE,
  INVALID_PAYLOAD,
  UNKNOWN_PAYMENT,
  /** The payment was created with a different PSP account than the one that signed the event. */
  FOREIGN_PAYMENT,
  /** The event's PSP reference is not the one stored for the payment, or none is stored. */
  PSP_REFERENCE_MISMATCH
}
