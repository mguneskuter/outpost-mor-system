package com.outpost.accounting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TransactionFamilyTest {
  @Test
  void paymentOwnsDirectCaptureAndRefundTransactions() {
    Transaction payment = AccountingFixtures.payment(1L);
    Transaction capture = AccountingFixtures.capture(2L);
    Transaction refund = AccountingFixtures.refund(3L);

    payment.attachChild(capture);
    payment.attachChild(refund);

    assertEquals(List.of(capture, refund), payment.getChildTransactions());
    assertEquals(payment, capture.getParentTransaction().orElseThrow());
    assertEquals(payment, refund.getParentTransaction().orElseThrow());
  }

  @Test
  void invalidAttachmentDoesNotMutateEitherSide() {
    Transaction payment = AccountingFixtures.payment(1L);
    Transaction capture = AccountingFixtures.capture(2L);
    Transaction refund = AccountingFixtures.refund(3L);

    assertThrows(IllegalArgumentException.class, () -> payment.attachChild(payment));
    assertThrows(
        IllegalArgumentException.class, () -> payment.attachChild(AccountingFixtures.payment(4L)));
    assertThrows(IllegalStateException.class, () -> capture.attachChild(refund));
    assertTrue(payment.getChildTransactions().isEmpty());
    assertTrue(capture.getParentTransaction().isEmpty());
    assertTrue(refund.getParentTransaction().isEmpty());

    payment.attachChild(capture);
    assertThrows(IllegalStateException.class, () -> payment.attachChild(capture));
    assertThrows(
        IllegalArgumentException.class, () -> payment.attachChild(AccountingFixtures.capture(2L)));
    assertEquals(List.of(capture), payment.getChildTransactions());
  }

  @Test
  void childNavigationIsReadOnly() {
    Transaction payment = AccountingFixtures.payment(1L);
    Transaction capture = AccountingFixtures.capture(2L);
    payment.attachChild(capture);

    assertThrows(
        UnsupportedOperationException.class, () -> payment.getChildTransactions().add(capture));
  }
}
