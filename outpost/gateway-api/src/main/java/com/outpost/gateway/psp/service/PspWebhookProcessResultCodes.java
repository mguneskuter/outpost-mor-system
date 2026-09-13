package com.outpost.gateway.psp.service;

/** Result codes of processing a PSP event notification. */
public enum PspWebhookProcessResultCodes {
  ACCEPTED,
  UNKNOWN_PSP,
  INVALID_SIGNATURE,
  INVALID_PAYLOAD,
  UNKNOWN_PAYMENT
}
