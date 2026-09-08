package com.outpost.common.iso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CountrySubdivisionsTest {
  @Test
  void containsAllSupportedEnumOwnedValues() {
    assertEquals(51, CountrySubdivisions.values().length);
    int index = 0;
    for (CountrySubdivisions constant : CountrySubdivisions.values()) {
      CountrySubdivision value = constant.value();
      assertEquals(index + 1L, value.countrySubdivisionId());
      assertEquals(Countries.UNITED_STATES.value(), value.country());
      assertTrue(value.code().matches("US-[A-Z0-9]+"));
      assertFalse(value.name().isBlank());
      index++;
    }
  }

  @Test
  void lookupReturnsCanonicalValues() {
    for (CountrySubdivisions constant : CountrySubdivisions.values()) {
      Optional<CountrySubdivision> found = CountrySubdivisions.fromCode(constant.value().code());
      assertTrue(found.isPresent());
      assertSame(constant.value(), found.orElseThrow());
    }
    assertFalse(CountrySubdivisions.fromCode("us-AK").isPresent());
    assertFalse(CountrySubdivisions.fromCode(" US-AK").isPresent());
  }
}
