package com.outpost.common.iso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CountrySubdivisionTest {
  @Test
  void isImmutableAndOwnedByTheEnum() {
    CountrySubdivision subdivision = CountrySubdivisions.US_AK.value();
    assertFalse(CountrySubdivision.class.isRecord());
    assertEquals(1L, subdivision.countrySubdivisionId());
    assertEquals(Countries.UNITED_STATES.value(), subdivision.country());
    assertEquals("US-AK", subdivision.code());
    assertEquals("Alaska", subdivision.name());
    assertSame(subdivision, CountrySubdivisions.US_AK.value());
    assertTrue(
        Arrays.stream(subdivision.getClass().getDeclaredConstructors())
            .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
  }
}
