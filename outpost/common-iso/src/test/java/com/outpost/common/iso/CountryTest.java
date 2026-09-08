package com.outpost.common.iso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.outpost.common.iso.Countries.Country;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CountryTest {
  @Test
  void isImmutableAndOwnedByTheEnum() {
    Country country = Countries.AUSTRIA.value();
    assertFalse(Country.class.isRecord());
    assertEquals(1L, country.countryId());
    assertEquals("AT", country.isoCode());
    assertEquals("Austria", country.name());
    assertSame(country, Countries.AUSTRIA.value());
    assertTrue(
        Arrays.stream(country.getClass().getDeclaredConstructors())
            .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
  }
}
