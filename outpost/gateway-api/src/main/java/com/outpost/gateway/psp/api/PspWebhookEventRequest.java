package com.outpost.gateway.psp.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.outpost.gateway.psp.service.PspWebhookEvent;
import com.outpost.payment.PspEventCodes;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** A PSP event notification as the PSP sends it. */
public record PspWebhookEventRequest(
    @JsonProperty("psp_code") String pspCode,
    @JsonProperty("psp_reference") String pspReference,
    @JsonProperty("psp_refund_reference") @Nullable String pspRefundReference,
    @JsonProperty("payment_reference") String paymentReference,
    @JsonProperty("event_code") PspEventCodes eventCode,
    long timestamp,
    boolean success,
    @JsonProperty("result_code") String resultCode,
    long amount,
    String currency,
    @JsonProperty("refund_reference") @Nullable String refundReference) {

  /** Rejects a notification missing a field its event code requires. */
  public PspWebhookEventRequest {
    Objects.requireNonNull(pspCode, "pspCode");
    Objects.requireNonNull(pspReference, "pspReference");
    Objects.requireNonNull(paymentReference, "paymentReference");
    Objects.requireNonNull(eventCode, "eventCode");
    Objects.requireNonNull(resultCode, "resultCode");
    Objects.requireNonNull(currency, "currency");
    if (eventCode == PspEventCodes.REFUND) {
      Objects.requireNonNull(pspRefundReference, "pspRefundReference");
    }
  }

  PspWebhookEvent toEvent() {
    String eventReference =
        eventCode == PspEventCodes.REFUND
            ? Objects.requireNonNull(pspRefundReference, "pspRefundReference")
            : pspReference;
    return new PspWebhookEvent(pspCode, pspReference, paymentReference, eventCode, eventReference);
  }
}
