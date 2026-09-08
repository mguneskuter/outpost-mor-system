package com.outpost.common.iso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.outpost.common.iso.Currencies.Currency;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CurrencyTest {
  @Test
  void isImmutableAndOwnedByTheEnum() {
    Currency currency = Currencies.EUR.value();
    assertFalse(Currency.class.isRecord());
    assertEquals(3L, currency.currencyId());
    assertEquals("EUR", currency.currencyCode());
    assertEquals(2, currency.exponent());
    assertSame(currency, Currencies.EUR.value());
    assertTrue(
        Arrays.stream(currency.getClass().getDeclaredConstructors())
            .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
  }
}
