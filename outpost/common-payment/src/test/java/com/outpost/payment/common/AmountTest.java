package com.outpost.payment.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AmountTest {
  private static final Currency EUR = Currencies.EUR.value();
  private static final Currency USD = Currencies.USD.value();

  @Test
  @SuppressWarnings("NullAway")
  void preservesSignedMinorUnitsAndValueEquality() {
    assertEquals(new Amount(EUR, 0), new Amount(EUR, 0));
    assertEquals(-125L, new Amount(EUR, -125).quantity());
    assertThrows(NullPointerException.class, () -> new Amount(null, 1));
  }

  @Test
  void addsAndSubtractsOnlyInTheSameCurrencyWithoutOverflow() {
    Amount amount = new Amount(EUR, 100);
    assertEquals(new Amount(EUR, 25), amount.plus(new Amount(EUR, -75)));
    assertEquals(new Amount(EUR, 175), amount.minus(new Amount(EUR, -75)));
    assertThrows(IllegalArgumentException.class, () -> amount.plus(new Amount(USD, 1)));
    assertThrows(IllegalArgumentException.class, () -> amount.minus(new Amount(USD, 1)));
    assertThrows(
        ArithmeticException.class, () -> new Amount(EUR, Long.MAX_VALUE).plus(new Amount(EUR, 1)));
    assertThrows(
        ArithmeticException.class, () -> new Amount(EUR, Long.MIN_VALUE).minus(new Amount(EUR, 1)));
  }

  @Test
  void negatesWithoutMutationOrOverflow() {
    Amount amount = new Amount(EUR, 125);
    Amount negated = amount.negated();
    assertEquals(new Amount(EUR, -125), negated);
    assertNotSame(amount, negated);
    assertThrows(ArithmeticException.class, () -> new Amount(EUR, Long.MIN_VALUE).negated());
  }

  @Test
  @SuppressWarnings("NullAway")
  void multipliesAndDividesWithHalfEvenRounding() {
    Amount amount = new Amount(EUR, 5);
    assertEquals(new Amount(EUR, 2), amount.multipliedBy(new BigDecimal("0.5")));
    assertEquals(new Amount(EUR, 4), new Amount(EUR, 9).multipliedBy(new BigDecimal("0.5")));
    assertEquals(new Amount(EUR, 2), amount.dividedBy(new BigDecimal("2")));
    assertEquals(new Amount(EUR, 2), new Amount(EUR, 5).dividedBy(new BigDecimal("2")));
    assertEquals(new Amount(EUR, 0), new Amount(EUR, 1).dividedBy(new BigDecimal("3")));
    assertEquals(new Amount(EUR, 1), new Amount(EUR, 2).dividedBy(new BigDecimal("3")));
    assertThrows(NullPointerException.class, () -> amount.multipliedBy(null));
    assertThrows(NullPointerException.class, () -> amount.dividedBy(null));
    assertThrows(ArithmeticException.class, () -> amount.dividedBy(BigDecimal.ZERO));
    assertThrows(
        ArithmeticException.class,
        () -> new Amount(EUR, Long.MAX_VALUE).multipliedBy(new BigDecimal("2")));
  }
}
