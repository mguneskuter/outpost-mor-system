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

class CountriesTest {

  private static final List<Country> EXPECTED =
      List.of(
          new Country(1L, "AT", "Austria"),
          new Country(2L, "BE", "Belgium"),
          new Country(3L, "BG", "Bulgaria"),
          new Country(4L, "CY", "Cyprus"),
          new Country(5L, "CZ", "Czechia"),
          new Country(6L, "DE", "Germany"),
          new Country(7L, "DK", "Denmark"),
          new Country(8L, "EE", "Estonia"),
          new Country(9L, "ES", "Spain"),
          new Country(10L, "FI", "Finland"),
          new Country(11L, "FR", "France"),
          new Country(12L, "GB", "United Kingdom"),
          new Country(13L, "GR", "Greece"),
          new Country(14L, "HR", "Croatia"),
          new Country(15L, "HU", "Hungary"),
          new Country(16L, "IE", "Ireland"),
          new Country(17L, "IT", "Italy"),
          new Country(18L, "LT", "Lithuania"),
          new Country(19L, "LU", "Luxembourg"),
          new Country(20L, "LV", "Latvia"),
          new Country(21L, "MT", "Malta"),
          new Country(22L, "NL", "Netherlands"),
          new Country(23L, "PL", "Poland"),
          new Country(24L, "PT", "Portugal"),
          new Country(25L, "RO", "Romania"),
          new Country(26L, "SE", "Sweden"),
          new Country(27L, "SI", "Slovenia"),
          new Country(28L, "SK", "Slovakia"),
          new Country(29L, "US", "United States"));

  @Test
  void containsExactlyTwentyNineEntriesWithRequiredValues() {
    Set<Country> actual =
        Arrays.stream(Countries.values()).map(Countries::value).collect(Collectors.toSet());
    assertEquals(EXPECTED.size(), actual.size());
    assertEquals(EXPECTED.stream().collect(Collectors.toSet()), actual);
  }

  @Test
  void countryIdsAreUnique() {
    long distinctIds =
        Arrays.stream(Countries.values())
            .map(Countries::value)
            .map(Country::countryId)
            .distinct()
            .count();
    assertEquals(EXPECTED.size(), distinctIds);
  }

  @Test
  void isoCodesAreUnique() {
    long distinctCodes =
        Arrays.stream(Countries.values())
            .map(Countries::value)
            .map(Country::isoCode)
            .distinct()
            .count();
    assertEquals(EXPECTED.size(), distinctCodes);
  }

  @Test
  void lookupReturnsEnumOwnedValueForEverySupportedCode() {
    for (Countries constant : Countries.values()) {
      String code = constant.value().isoCode();
      Optional<Country> found = Countries.fromIsoCode(code);
      assertTrue(found.isPresent());
      assertEquals(constant.value(), found.orElseThrow());
    }
  }

  @Test
  void gbResolvesToUnitedKingdomAndUkDoesNot() {
    assertEquals(Countries.UNITED_KINGDOM.value(), Countries.fromIsoCode("GB").orElseThrow());
    assertFalse(Countries.fromIsoCode("UK").isPresent());
  }

  @Test
  void lookupIsCaseSensitiveAndUntrimmed() {
    assertTrue(Countries.fromIsoCode("US").isPresent());
    assertFalse(Countries.fromIsoCode("us").isPresent());
    assertFalse(Countries.fromIsoCode("US ").isPresent());
    assertFalse(Countries.fromIsoCode(" US").isPresent());
    assertFalse(Countries.fromIsoCode("").isPresent());
    assertFalse(Countries.fromIsoCode("DEU").isPresent());
    assertFalse(Countries.fromIsoCode("D1").isPresent());
    assertFalse(Countries.fromIsoCode("XX").isPresent());
  }

  @Test
  @SuppressWarnings("NullAway")
  void rejectsNullLookupArgument() {
    assertThrows(NullPointerException.class, () -> Countries.fromIsoCode((String) null));
  }

  @Test
  void everyExpectedEntryResolvesToItsOwnValue() {
    for (Country expectedCountry : EXPECTED) {
      Country resolved = Countries.fromIsoCode(expectedCountry.isoCode()).orElseThrow();
      assertEquals(expectedCountry, resolved);
      assertEquals(expectedCountry.countryId(), resolved.countryId());
    }
  }
}
