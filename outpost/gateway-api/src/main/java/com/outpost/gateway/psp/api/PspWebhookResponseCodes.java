package com.outpost.gateway.psp.api;

import com.outpost.framework.logging.LogFields;
import com.outpost.framework.logging.StructuredLogField;
import com.outpost.framework.logging.StructuredLogger;
import com.outpost.gateway.psp.service.PspWebhookProcessResultCodes;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Answers PSP event notifications. Every notification that is not queued is logged once, without
 * its payload, under its {@code rejection_reason}.
 */
final class PspWebhookResponseCodes {
  private static final StructuredLogger LOGGER =
      new StructuredLogger(LoggerFactory.getLogger(PspWebhookResponseCodes.class));

  private PspWebhookResponseCodes() {}

  static ResponseEntity<PspWebhookEventResponse> respond(PspWebhookProcessResultCodes code) {
    if (code != PspWebhookProcessResultCodes.ACCEPTED) {
      LOGGER.warn(
          "PSP webhook rejected", new StructuredLogField(LogField.REJECTION_REASON, code.name()));
    }
    return ResponseEntity.status(status(code)).body(new PspWebhookEventResponse(code.name()));
  }

  private static HttpStatus status(PspWebhookProcessResultCodes code) {
    return switch (code) {
      case ACCEPTED -> HttpStatus.OK;
      case UNKNOWN_PSP -> HttpStatus.NOT_FOUND;
      case INVALID_SIGNATURE -> HttpStatus.UNAUTHORIZED;
      case INVALID_PAYLOAD -> HttpStatus.BAD_REQUEST;
      // An authenticated event that matches no stored payment is acknowledged as processed with a
      // negative result, so the PSP does not redeliver it; it is never queued.
      case UNKNOWN_PAYMENT, FOREIGN_PAYMENT, PSP_REFERENCE_MISMATCH -> HttpStatus.OK;
      // A full queue refuses the event without acknowledging it, so the PSP redelivers it.
      case QUEUE_FULL -> HttpStatus.SERVICE_UNAVAILABLE;
    };
  }

  private enum LogField implements LogFields {
    REJECTION_REASON("rejection_reason");

    private final String jsonKey;

    LogField(String jsonKey) {
      this.jsonKey = jsonKey;
    }

    @Override
    public String getJsonKey() {
      return jsonKey;
    }
  }
}
