package com.outpost.common.iso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.outpost.common.iso.Countries.Country;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CountriesTest {
  private static final String[] CODES = {
    "AT", "BE", "BG", "CY", "CZ", "DE", "DK", "EE", "ES", "FI", "FR", "GB", "GR", "HR", "HU", "IE",
    "IT", "LT", "LU", "LV", "MT", "NL", "PL", "PT", "RO", "SE", "SI", "SK", "US"
  };
  private static final String[] NAMES = {
    "Austria",
    "Belgium",
    "Bulgaria",
    "Cyprus",
    "Czechia",
    "Germany",
    "Denmark",
    "Estonia",
    "Spain",
    "Finland",
    "France",
    "United Kingdom",
    "Greece",
    "Croatia",
    "Hungary",
    "Ireland",
    "Italy",
    "Lithuania",
    "Luxembourg",
    "Latvia",
    "Malta",
    "Netherlands",
    "Poland",
    "Portugal",
    "Romania",
    "Sweden",
    "Slovenia",
    "Slovakia",
    "United States"
  };

  @Test
  void containsExactEnumOwnedValues() {
    assertEquals(CODES.length, Countries.values().length);
    int index = 0;
    for (Countries constant : Countries.values()) {
      Country value = constant.value();
      assertEquals(index + 1L, value.countryId());
      assertEquals(CODES[index], value.isoCode());
      assertEquals(NAMES[index], value.name());
      index++;
    }
  }

  @Test
  void lookupReturnsCanonicalValues() {
    for (Countries constant : Countries.values()) {
      Optional<Country> found = Countries.fromIsoCode(constant.value().isoCode());
      assertTrue(found.isPresent());
      assertSame(constant.value(), found.orElseThrow());
    }
    assertFalse(Countries.fromIsoCode("UK").isPresent());
    assertFalse(Countries.fromIsoCode("at").isPresent());
    assertFalse(Countries.fromIsoCode(" AT").isPresent());
  }
}
