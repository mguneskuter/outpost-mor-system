package com.outpost.common.iso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.outpost.common.iso.Currencies.Currency;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CurrenciesTest {
  @Test
  void lookupReturnsCanonicalValues() {
    Optional<Currency> found = Currencies.fromCurrencyCode("EUR");

    assertTrue(found.isPresent());
    assertSame(Currencies.EUR.getValue(), found.orElseThrow());
    assertEquals(2, found.orElseThrow().getExponent());
  }

  @Test
  void lookupIsExactAndCaseSensitive() {
    assertFalse(Currencies.fromCurrencyCode("eur").isPresent());
    assertFalse(Currencies.fromCurrencyCode(" EUR").isPresent());
  }

  @Test
  @SuppressWarnings("NullAway")
  void lookupRejectsNullCode() {
    assertThrows(NullPointerException.class, () -> Currencies.fromCurrencyCode(null));
  }
}
