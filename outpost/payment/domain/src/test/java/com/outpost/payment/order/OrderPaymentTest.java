package com.outpost.payment.order;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OrderPaymentTest {
  @Test
  void acceptsAnAbsentPspReferenceBeforeThePspConfirmsThePayment() {
    OrderPayment payment =
        new OrderPayment(1L, 10L, "payment-ref", 300L, null, OrderFixtures.CREATED);

    assertTrue(payment.getPspReference().isEmpty());
  }

  @Test
  void rejectsBlankPaymentReference() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new OrderPayment(1L, 10L, " ", 300L, null, OrderFixtures.CREATED));
  }
}
