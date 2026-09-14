package com.outpost.gateway.psp.service;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A PSP event notification about one order.
 *
 * @param orderReference the original reference the PSP echoes as {@code payment_reference}
 * @param pspReference the PSP's reference for the order the event concerns
 * @param resultCode the PSP's own outcome code, such as its reason for a refusal
 * @param refundReference Outpost's refund reference; present on a REFUND event
 * @param pspRefundReference the PSP's reference for the refund; present on a REFUND event once the
 *     PSP has one
 * @param amount the event's amount in minor units of {@code currency}; a REFUND event echoes the
 *     refund's gross
 * @param refundLines the refunded lines a REFUND event echoes; empty when the event carries none
 */
public record PspOrderEvent(
    String pspCode,
    String pspReference,
    String orderReference,
    PspEventCodes eventCode,
    boolean success,
    String resultCode,
    @Nullable String refundReference,
    @Nullable String pspRefundReference,
    long amount,
    String currency,
    List<PspRefundLine> refundLines) {
  /** Copies {@code refundLines}. */
  public PspOrderEvent {
    refundLines = List.copyOf(Objects.requireNonNull(refundLines, "refundLines"));
  }
}
