package com.outpost.gateway.psp.service;

/** Outcomes from verifying and recording a PSP event notification. */
public enum PspWebhookIntakeResults {
  RECORDED,
  UNKNOWN_PSP,
  INVALID_SIGNATURE,
  INVALID_PAYLOAD,
  UNKNOWN_OR_FOREIGN_PAYMENT
}
