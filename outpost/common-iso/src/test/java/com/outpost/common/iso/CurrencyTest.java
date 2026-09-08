package com.outpost.common.iso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CurrencyTest {

  @Test
  void isRecordAndEqualsByValue() {
    Currency currency = new Currency(3L, "EUR", 2);
    assertTrue(Currency.class.isRecord());
    assertEquals(new Currency(3L, "EUR", 2), currency);
    assertEquals(3L, currency.currencyId());
    assertEquals("EUR", currency.currencyCode());
    assertEquals(2, currency.exponent());
  }

  @Test
  void rejectsNonPositiveCurrencyId() {
    assertThrows(IllegalArgumentException.class, () -> new Currency(0L, "EUR", 2));
    assertThrows(IllegalArgumentException.class, () -> new Currency(-1L, "EUR", 2));
  }

  @Test
  @SuppressWarnings("NullAway")
  void rejectsNullCurrencyCode() {
    assertThrows(IllegalArgumentException.class, () -> new Currency(3L, (String) null, 2));
  }

  @Test
  void rejectsBlankCurrencyCode() {
    assertThrows(IllegalArgumentException.class, () -> new Currency(3L, "", 2));
    assertThrows(IllegalArgumentException.class, () -> new Currency(3L, "   ", 2));
  }

  @Test
  void rejectsCurrencyCodeThatIsNotThreeUppercaseLetters() {
    assertThrows(IllegalArgumentException.class, () -> new Currency(3L, "E", 2));
    assertThrows(IllegalArgumentException.class, () -> new Currency(3L, "EU", 2));
    assertThrows(IllegalArgumentException.class, () -> new Currency(3L, "EURX", 2));
    assertThrows(IllegalArgumentException.class, () -> new Currency(3L, "eur", 2));
    assertThrows(IllegalArgumentException.class, () -> new Currency(3L, "E1R", 2));
    assertThrows(IllegalArgumentException.class, () -> new Currency(3L, " EUR", 2));
  }

  @Test
  void rejectsNegativeExponent() {
    assertThrows(IllegalArgumentException.class, () -> new Currency(3L, "EUR", -1));
  }
}
