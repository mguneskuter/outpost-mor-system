package com.outpost.common.iso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CountrySubdivisionsTest {
  @Test
  void lookupReturnsCanonicalValues() {
    Optional<CountrySubdivision> found = CountrySubdivisions.fromCode("US-AK");

    assertTrue(found.isPresent());
    assertSame(CountrySubdivisions.US_AK.getValue(), found.orElseThrow());
    assertEquals("Alaska", found.orElseThrow().getName());
  }

  @Test
  void lookupByCountryAcceptsMatchingCountry() {
    assertSame(
        CountrySubdivisions.US_AK.getValue(),
        CountrySubdivisions.fromCode(Countries.UNITED_STATES.getValue(), "US-AK").orElseThrow());
  }

  @Test
  void lookupByCountryRejectsSubdivisionOwnedByAnotherCountry() {
    assertFalse(CountrySubdivisions.fromCode(Countries.AUSTRIA.getValue(), "US-AK").isPresent());
  }

  @Test
  void lookupIsExactAndCaseSensitive() {
    assertFalse(CountrySubdivisions.fromCode("us-AK").isPresent());
    assertFalse(CountrySubdivisions.fromCode(" US-AK").isPresent());
  }

  @Test
  @SuppressWarnings("NullAway")
  void lookupRejectsNullArguments() {
    assertThrows(NullPointerException.class, () -> CountrySubdivisions.fromCode((String) null));
    assertThrows(NullPointerException.class, () -> CountrySubdivisions.fromCode(null, "US-AK"));
    assertThrows(
        NullPointerException.class,
        () -> CountrySubdivisions.fromCode(Countries.UNITED_STATES.getValue(), null));
  }
}
