package com.outpost.gateway.psp.service;

/** The PSP events Gateway accepts by webhook, serialised as {@code event_code}. */
public enum PspWebhookEventCodes {
  AUTHORISATION,
  CAPTURE,
  REFUND
}
