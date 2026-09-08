package com.outpost.common.iso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CurrenciesTest {

  private static final List<Currency> EXPECTED =
      List.of(
          new Currency(1L, "CZK", 2),
          new Currency(2L, "DKK", 2),
          new Currency(3L, "EUR", 2),
          new Currency(4L, "GBP", 2),
          new Currency(5L, "HUF", 2),
          new Currency(6L, "PLN", 2),
          new Currency(7L, "RON", 2),
          new Currency(8L, "SEK", 2),
          new Currency(9L, "USD", 2));

  @Test
  void containsExactlyNineEntriesWithRequiredValues() {
    Set<Currency> actual =
        Arrays.stream(Currencies.values()).map(Currencies::value).collect(Collectors.toSet());
    assertEquals(EXPECTED.size(), actual.size());
    assertEquals(EXPECTED.stream().collect(Collectors.toSet()), actual);
  }

  @Test
  void currencyIdsAreUnique() {
    long distinctIds =
        Arrays.stream(Currencies.values())
            .map(Currencies::value)
            .map(Currency::currencyId)
            .distinct()
            .count();
    assertEquals(EXPECTED.size(), distinctIds);
  }

  @Test
  void currencyCodesAreUnique() {
    long distinctCodes =
        Arrays.stream(Currencies.values())
            .map(Currencies::value)
            .map(Currency::currencyCode)
            .distinct()
            .count();
    assertEquals(EXPECTED.size(), distinctCodes);
  }

  @Test
  void everyExponentIsTwo() {
    assertTrue(
        Arrays.stream(Currencies.values())
            .map(Currencies::value)
            .allMatch(currency -> currency.exponent() == 2));
  }

  @Test
  void lookupReturnsEnumOwnedValueForEverySupportedCode() {
    for (Currencies constant : Currencies.values()) {
      String code = constant.value().currencyCode();
      Optional<Currency> found = Currencies.fromCurrencyCode(code);
      assertTrue(found.isPresent());
      assertEquals(constant.value(), found.orElseThrow());
    }
  }

  @Test
  void lookupDoesNotContainBgn() {
    assertFalse(Currencies.fromCurrencyCode("BGN").isPresent());
  }

  @Test
  void lookupIsCaseSensitiveAndUntrimmed() {
    assertTrue(Currencies.fromCurrencyCode("EUR").isPresent());
    assertFalse(Currencies.fromCurrencyCode("eur").isPresent());
    assertFalse(Currencies.fromCurrencyCode("EUR ").isPresent());
    assertFalse(Currencies.fromCurrencyCode(" EUR").isPresent());
    assertFalse(Currencies.fromCurrencyCode("").isPresent());
    assertFalse(Currencies.fromCurrencyCode("EURO").isPresent());
    assertFalse(Currencies.fromCurrencyCode("E1R").isPresent());
    assertFalse(Currencies.fromCurrencyCode("XXX").isPresent());
  }

  @Test
  @SuppressWarnings("NullAway")
  void rejectsNullLookupArgument() {
    assertThrows(NullPointerException.class, () -> Currencies.fromCurrencyCode((String) null));
  }

  @Test
  void everyExpectedEntryResolvesToItsOwnValue() {
    for (Currency expectedCurrency : EXPECTED) {
      Currency resolved =
          Currencies.fromCurrencyCode(expectedCurrency.currencyCode()).orElseThrow();
      assertEquals(expectedCurrency, resolved);
      assertEquals(expectedCurrency.currencyId(), resolved.currencyId());
    }
  }
}
