package com.outpost.common.iso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CountrySubdivisionTest {

  @Test
  void isRecordAndEqualsByValue() {
    CountrySubdivision subdivision =
        new CountrySubdivision(1L, Countries.UNITED_STATES.value(), "US-AK", "Alaska");
    assertTrue(CountrySubdivision.class.isRecord());
    assertEquals(
        new CountrySubdivision(1L, Countries.UNITED_STATES.value(), "US-AK", "Alaska"),
        subdivision);
    assertEquals(1L, subdivision.countrySubdivisionId());
    assertEquals(Countries.UNITED_STATES.value(), subdivision.country());
    assertEquals("US-AK", subdivision.code());
    assertEquals("Alaska", subdivision.name());
  }

  @Test
  void rejectsNonPositiveSubdivisionId() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CountrySubdivision(0L, Countries.UNITED_STATES.value(), "US-AK", "Alaska"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CountrySubdivision(-1L, Countries.UNITED_STATES.value(), "US-AK", "Alaska"));
  }

  @Test
  @SuppressWarnings("NullAway")
  void rejectsNullCountry() {
    assertThrows(
        IllegalArgumentException.class, () -> new CountrySubdivision(1L, null, "US-AK", "Alaska"));
  }

  @Test
  @SuppressWarnings("NullAway")
  void rejectsNullAndBlankCode() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CountrySubdivision(1L, Countries.UNITED_STATES.value(), null, "Alaska"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CountrySubdivision(1L, Countries.UNITED_STATES.value(), "", "Alaska"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CountrySubdivision(1L, Countries.UNITED_STATES.value(), "   ", "Alaska"));
  }

  @Test
  void rejectsCodeWithWrongShape() {
    Country unitedStates = Countries.UNITED_STATES.value();
    assertThrows(
        IllegalArgumentException.class,
        () -> new CountrySubdivision(1L, unitedStates, "AK", "Alaska"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CountrySubdivision(1L, unitedStates, "US-AK-1", "Alaska"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CountrySubdivision(1L, unitedStates, "us-ak", "Alaska"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CountrySubdivision(1L, unitedStates, "US-_", "Alaska"));
  }

  @Test
  void rejectsCodeWhoseCountryPrefixDoesNotMatch() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CountrySubdivision(
                1L, Countries.UNITED_STATES.value(), "CA-BC", "British Columbia"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CountrySubdivision(1L, Countries.UNITED_STATES.value(), "GB-LON", "London"));
  }

  @Test
  @SuppressWarnings("NullAway")
  void rejectsNullAndBlankName() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CountrySubdivision(1L, Countries.UNITED_STATES.value(), "US-AK", null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CountrySubdivision(1L, Countries.UNITED_STATES.value(), "US-AK", ""));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CountrySubdivision(1L, Countries.UNITED_STATES.value(), "US-AK", "   "));
  }
}
