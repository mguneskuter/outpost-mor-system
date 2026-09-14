package com.outpost.integration.psp;

import com.outpost.payment.common.Amount;
import java.util.List;
import java.util.Objects;

/**
 * Request to refund {@code amount} of an order at a payment service provider.
 *
 * @param pspReference the PSP's reference for the order
 * @param orderReference Outpost's reference for the order
 * @param refundReference Outpost's reference for the refund, which the PSP echoes
 * @param amount the refund total, the sum of the lines' gross amounts
 * @param lines the refunded order lines, at least one, which the PSP echoes
 */
public record RefundPspOrderRequest(
    String pspCode,
    String pspReference,
    String orderReference,
    String refundReference,
    Amount amount,
    List<RefundPspOrderLine> lines) {
  /**
   * Copies {@code lines}.
   *
   * @throws IllegalArgumentException when {@code lines} is empty
   */
  public RefundPspOrderRequest {
    Objects.requireNonNull(lines, "lines");
    if (lines.isEmpty()) {
      throw new IllegalArgumentException("A refund must cover at least one order line");
    }
    lines = List.copyOf(lines);
  }
}
