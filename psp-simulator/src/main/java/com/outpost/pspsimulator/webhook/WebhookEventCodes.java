package com.outpost.pspsimulator.webhook;

/** The lifecycle events the simulator reports by webhook, serialised as {@code event_code}. */
public enum WebhookEventCodes {
  AUTHORISATION,
  CAPTURE,
  REFUND;

  /** Returns the exact event code, which is also the serialised value. */
  public String getCode() {
    return name();
  }
}
