package com.outpost.gateway.psp.api;

import com.outpost.framework.logging.LogFields;
import com.outpost.framework.logging.StructuredLogField;
import com.outpost.framework.logging.StructuredLogger;
import com.outpost.gateway.psp.service.PspWebhookProcessResultCodes;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Answers PSP event notifications. Every notification that is not recorded is logged without its
 * payload and counted in {@code outpost.webhook.rejected}, tagged by {@code reason}.
 */
public final class PspWebhookResponses {
  private static final StructuredLogger LOGGER =
      new StructuredLogger(LoggerFactory.getLogger(PspWebhookResponses.class));
  private static final String REJECTED_METER = "outpost.webhook.rejected";
  private final MeterRegistry meterRegistry;

  /** Creates responses that count rejections in {@code meterRegistry}. */
  public PspWebhookResponses(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  ResponseEntity<PspWebhookEventResponse> respond(PspWebhookProcessResultCodes code) {
    if (code != PspWebhookProcessResultCodes.ACCEPTED) {
      LOGGER.warn(
          "PSP webhook rejected", new StructuredLogField(LogField.REJECTION_REASON, code.name()));
      meterRegistry.counter(REJECTED_METER, "reason", code.name()).increment();
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
      // negative result, so the PSP does not redeliver it; it is never recorded.
      case UNKNOWN_PAYMENT, FOREIGN_PAYMENT, PSP_REFERENCE_MISMATCH -> HttpStatus.OK;
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
