package com.outpost.gateway.psp.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.outpost.gateway.psp.service.PspEventCodes;
import com.outpost.gateway.psp.service.PspOrderEvent;
import com.outpost.gateway.psp.service.PspRefundLine;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** A PSP event notification as the PSP sends it. */
public record PspWebhookEvent(
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
    @JsonProperty("refund_reference") @Nullable String refundReference,
    @JsonProperty("refund_lines") @Nullable List<RefundLine> refundLines) {

  /** Rejects a notification missing a field its event code requires. */
  public PspWebhookEvent {
    Objects.requireNonNull(pspCode, "pspCode");
    Objects.requireNonNull(pspReference, "pspReference");
    Objects.requireNonNull(paymentReference, "paymentReference");
    Objects.requireNonNull(eventCode, "eventCode");
    Objects.requireNonNull(resultCode, "resultCode");
    Objects.requireNonNull(currency, "currency");
  }

  /** One refunded order line as the PSP echoes it. */
  public record RefundLine(
      @JsonProperty("order_line_reference") String orderLineReference,
      @JsonProperty("tax_rate") BigDecimal taxRate,
      @JsonProperty("net_amount") long netAmount,
      @JsonProperty("gross_amount") long grossAmount) {
    /** Rejects a line missing its reference or rate. */
    public RefundLine {
      Objects.requireNonNull(orderLineReference, "orderLineReference");
      Objects.requireNonNull(taxRate, "taxRate");
    }
  }

  PspOrderEvent toOrderEvent() {
    return new PspOrderEvent(
        pspCode,
        pspReference,
        paymentReference,
        eventCode,
        success,
        resultCode,
        refundReference,
        pspRefundReference,
        amount,
        currency,
        refundLines == null
            ? List.of()
            : refundLines.stream()
                .map(
                    line ->
                        new PspRefundLine(
                            line.orderLineReference(),
                            line.taxRate(),
                            line.netAmount(),
                            line.grossAmount()))
                .toList());
  }
}
