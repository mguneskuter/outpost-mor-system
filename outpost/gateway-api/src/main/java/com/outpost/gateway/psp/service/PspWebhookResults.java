package com.outpost.gateway.psp.service;

/** Result codes of processing a PSP event notification. */
public enum PspWebhookResults {
  ACCEPTED,
  UNKNOWN_PSP,
  INVALID_SIGNATURE,
  INVALID_PAYLOAD,
  /** No stored order has the event's reference and the signing PSP's account. */
  UNKNOWN_ORDER,
  /** The event's PSP reference is not the one stored for the order, or none is stored. */
  PSP_REFERENCE_MISMATCH,
  /** The event matches its order, but the accounting queue holds its capacity. */
  QUEUE_FULL
}
