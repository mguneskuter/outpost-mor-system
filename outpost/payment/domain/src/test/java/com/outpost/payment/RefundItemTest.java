package com.outpost.payment;

import static com.outpost.payment.PaymentFixtures.eur;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import org.junit.jupiter.api.Test;

class RefundItemTest {
  @Test
  void rejectsNegativeNetAmount() {
    assertThrows(IllegalArgumentException.class, () -> new RefundItem(1L, 2L, eur(-1L), eur(0L)));
  }

  @Test
  void rejectsMismatchedCurrenciesBetweenNetAndTaxAmounts() {
    Amount usdTax = new Amount(Currencies.USD.getValue(), 20L);

    assertThrows(IllegalArgumentException.class, () -> new RefundItem(1L, 2L, eur(100L), usdTax));
  }
}
