package com.outpost.common.iso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.outpost.common.iso.Currencies.Currency;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CurrenciesTest {
  private static final String[] CODES = {
    "CZK", "DKK", "EUR", "GBP", "HUF", "PLN", "RON", "SEK", "USD"
  };

  @Test
  void containsExactEnumOwnedValues() {
    assertEquals(CODES.length, Currencies.values().length);
    int index = 0;
    for (Currencies constant : Currencies.values()) {
      Currency value = constant.value();
      assertEquals(index + 1L, value.currencyId());
      assertEquals(CODES[index], value.currencyCode());
      assertEquals(2, value.exponent());
      index++;
    }
  }

  @Test
  void lookupReturnsCanonicalValues() {
    for (Currencies constant : Currencies.values()) {
      Optional<Currency> found = Currencies.fromCurrencyCode(constant.value().currencyCode());
      assertTrue(found.isPresent());
      assertSame(constant.value(), found.orElseThrow());
    }
    assertFalse(Currencies.fromCurrencyCode("eur").isPresent());
    assertFalse(Currencies.fromCurrencyCode(" EUR").isPresent());
  }
}
