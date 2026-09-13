package com.outpost.payment.refund;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RefundTest {
  @Test
  void rejectsBlankRefundReference() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Refund(null, " ", 1L, "order-1", "merchant-refund-1", "key-1", "77", null));
  }

  @Test
  void rejectsNonPositiveOrderId() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Refund(null, "refund-1", 0L, "order-1", "merchant-refund-1", "key-1", "77", null));
  }
}
