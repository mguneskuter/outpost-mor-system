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

    assertEquals(List.of(capture, refund), payment.childTransactions());
    assertEquals(payment, capture.parentTransaction().orElseThrow());
    assertEquals(payment, refund.parentTransaction().orElseThrow());
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
    assertTrue(payment.childTransactions().isEmpty());
    assertTrue(capture.parentTransaction().isEmpty());
    assertTrue(refund.parentTransaction().isEmpty());

    payment.attachChild(capture);
    assertThrows(IllegalStateException.class, () -> payment.attachChild(capture));
    assertThrows(
        IllegalArgumentException.class, () -> payment.attachChild(AccountingFixtures.capture(2L)));
    assertEquals(List.of(capture), payment.childTransactions());
  }

  @Test
  void childNavigationIsReadOnly() {
    Transaction payment = AccountingFixtures.payment(1L);
    Transaction capture = AccountingFixtures.capture(2L);
    payment.attachChild(capture);

    assertThrows(
        UnsupportedOperationException.class, () -> payment.childTransactions().add(capture));
  }
}
