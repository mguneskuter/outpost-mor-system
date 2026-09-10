package com.outpost.common.iso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.outpost.common.iso.Countries.Country;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CountriesTest {
  @Test
  void lookupReturnsCanonicalValues() {
    Optional<Country> found = Countries.fromIsoCode("NL");

    assertTrue(found.isPresent());
    assertSame(Countries.NETHERLANDS.getValue(), found.orElseThrow());
    assertEquals("Netherlands", found.orElseThrow().getName());
  }

  @Test
  void lookupIsExactAndCaseSensitive() {
    assertFalse(Countries.fromIsoCode("UK").isPresent());
    assertFalse(Countries.fromIsoCode("at").isPresent());
    assertFalse(Countries.fromIsoCode(" AT").isPresent());
  }

  @Test
  @SuppressWarnings("NullAway")
  void lookupRejectsNullCode() {
    assertThrows(NullPointerException.class, () -> Countries.fromIsoCode(null));
  }
}
