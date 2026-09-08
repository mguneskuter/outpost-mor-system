package com.outpost.common.iso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CountryTest {

  @Test
  void isRecordAndEqualsByValue() {
    Country country = new Country(1L, "AT", "Austria");
    assertTrue(Country.class.isRecord());
    assertEquals(new Country(1L, "AT", "Austria"), country);
    assertEquals(1L, country.countryId());
    assertEquals("AT", country.isoCode());
    assertEquals("Austria", country.name());
  }

  @Test
  void rejectsNonPositiveCountryId() {
    assertThrows(IllegalArgumentException.class, () -> new Country(0L, "AT", "Austria"));
    assertThrows(IllegalArgumentException.class, () -> new Country(-1L, "AT", "Austria"));
  }

  @Test
  @SuppressWarnings("NullAway")
  void rejectsNullIsoCode() {
    assertThrows(IllegalArgumentException.class, () -> new Country(1L, (String) null, "Austria"));
  }

  @Test
  void rejectsBlankIsoCode() {
    assertThrows(IllegalArgumentException.class, () -> new Country(1L, "", "Austria"));
    assertThrows(IllegalArgumentException.class, () -> new Country(1L, "  ", "Austria"));
  }

  @Test
  void rejectsIsoCodeThatIsNotTwoUppercaseLetters() {
    assertThrows(IllegalArgumentException.class, () -> new Country(1L, "A", "Austria"));
    assertThrows(IllegalArgumentException.class, () -> new Country(1L, "ABC", "Austria"));
    assertThrows(IllegalArgumentException.class, () -> new Country(1L, "at", "Austria"));
    assertThrows(IllegalArgumentException.class, () -> new Country(1L, "A1", "Austria"));
    assertThrows(IllegalArgumentException.class, () -> new Country(1L, "AT ", "Austria"));
    assertThrows(IllegalArgumentException.class, () -> new Country(1L, " AT", "Austria"));
  }

  @Test
  @SuppressWarnings("NullAway")
  void rejectsNullName() {
    assertThrows(IllegalArgumentException.class, () -> new Country(1L, "AT", (String) null));
  }

  @Test
  void rejectsBlankName() {
    assertThrows(IllegalArgumentException.class, () -> new Country(1L, "AT", ""));
    assertThrows(IllegalArgumentException.class, () -> new Country(1L, "AT", "   "));
  }
}
