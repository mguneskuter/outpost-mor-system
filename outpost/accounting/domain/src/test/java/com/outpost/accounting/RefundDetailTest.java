package com.outpost.accounting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import org.junit.jupiter.api.Test;

class RefundDetailTest {
  @Test
  void retainsRefundAndItsAmounts() {
    Transaction payment = AccountingFixtures.payment(1L);
    Transaction refund = AccountingFixtures.refund(2L);
    payment.attachChild(refund);
    Amount net = new Amount(Currencies.EUR.getValue(), 80L);
    Amount tax = new Amount(Currencies.EUR.getValue(), 20L);

    RefundDetail detail = new RefundDetail(refund, net, tax);

    assertEquals(refund, detail.getRefundTransaction());
    assertEquals(net, detail.getNetAmount());
    assertEquals(tax, detail.getTaxAmount());
  }

  @Test
  void rejectsDetailsThatDoNotDescribePaymentRefund() {
    Transaction unattachedRefund = AccountingFixtures.refund(1L);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RefundDetail(
                unattachedRefund,
                new Amount(Currencies.EUR.getValue(), 80L),
                new Amount(Currencies.EUR.getValue(), 20L)));

    Transaction payment = AccountingFixtures.payment(2L);
    Transaction refund = AccountingFixtures.refund(3L);
    payment.attachChild(refund);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RefundDetail(
                refund,
                new Amount(Currencies.USD.getValue(), 80L),
                new Amount(Currencies.USD.getValue(), 20L)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RefundDetail(
                refund,
                new Amount(Currencies.EUR.getValue(), 81L),
                new Amount(Currencies.EUR.getValue(), 20L)));
  }
}
