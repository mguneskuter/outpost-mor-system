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

class CountrySubdivisionsTest {

  private static final Country UNITED_STATES = Countries.UNITED_STATES.value();

  private static final List<CountrySubdivision> EXPECTED =
      List.of(
          new CountrySubdivision(1L, UNITED_STATES, "US-AK", "Alaska"),
          new CountrySubdivision(2L, UNITED_STATES, "US-AL", "Alabama"),
          new CountrySubdivision(3L, UNITED_STATES, "US-AR", "Arkansas"),
          new CountrySubdivision(4L, UNITED_STATES, "US-AZ", "Arizona"),
          new CountrySubdivision(5L, UNITED_STATES, "US-CA", "California"),
          new CountrySubdivision(6L, UNITED_STATES, "US-CO", "Colorado"),
          new CountrySubdivision(7L, UNITED_STATES, "US-CT", "Connecticut"),
          new CountrySubdivision(8L, UNITED_STATES, "US-DC", "District of Columbia"),
          new CountrySubdivision(9L, UNITED_STATES, "US-DE", "Delaware"),
          new CountrySubdivision(10L, UNITED_STATES, "US-FL", "Florida"),
          new CountrySubdivision(11L, UNITED_STATES, "US-GA", "Georgia"),
          new CountrySubdivision(12L, UNITED_STATES, "US-HI", "Hawaii"),
          new CountrySubdivision(13L, UNITED_STATES, "US-IA", "Iowa"),
          new CountrySubdivision(14L, UNITED_STATES, "US-ID", "Idaho"),
          new CountrySubdivision(15L, UNITED_STATES, "US-IL", "Illinois"),
          new CountrySubdivision(16L, UNITED_STATES, "US-IN", "Indiana"),
          new CountrySubdivision(17L, UNITED_STATES, "US-KS", "Kansas"),
          new CountrySubdivision(18L, UNITED_STATES, "US-KY", "Kentucky"),
          new CountrySubdivision(19L, UNITED_STATES, "US-LA", "Louisiana"),
          new CountrySubdivision(20L, UNITED_STATES, "US-MA", "Massachusetts"),
          new CountrySubdivision(21L, UNITED_STATES, "US-MD", "Maryland"),
          new CountrySubdivision(22L, UNITED_STATES, "US-ME", "Maine"),
          new CountrySubdivision(23L, UNITED_STATES, "US-MI", "Michigan"),
          new CountrySubdivision(24L, UNITED_STATES, "US-MN", "Minnesota"),
          new CountrySubdivision(25L, UNITED_STATES, "US-MO", "Missouri"),
          new CountrySubdivision(26L, UNITED_STATES, "US-MS", "Mississippi"),
          new CountrySubdivision(27L, UNITED_STATES, "US-MT", "Montana"),
          new CountrySubdivision(28L, UNITED_STATES, "US-NC", "North Carolina"),
          new CountrySubdivision(29L, UNITED_STATES, "US-ND", "North Dakota"),
          new CountrySubdivision(30L, UNITED_STATES, "US-NE", "Nebraska"),
          new CountrySubdivision(31L, UNITED_STATES, "US-NH", "New Hampshire"),
          new CountrySubdivision(32L, UNITED_STATES, "US-NJ", "New Jersey"),
          new CountrySubdivision(33L, UNITED_STATES, "US-NM", "New Mexico"),
          new CountrySubdivision(34L, UNITED_STATES, "US-NV", "Nevada"),
          new CountrySubdivision(35L, UNITED_STATES, "US-NY", "New York"),
          new CountrySubdivision(36L, UNITED_STATES, "US-OH", "Ohio"),
          new CountrySubdivision(37L, UNITED_STATES, "US-OK", "Oklahoma"),
          new CountrySubdivision(38L, UNITED_STATES, "US-OR", "Oregon"),
          new CountrySubdivision(39L, UNITED_STATES, "US-PA", "Pennsylvania"),
          new CountrySubdivision(40L, UNITED_STATES, "US-RI", "Rhode Island"),
          new CountrySubdivision(41L, UNITED_STATES, "US-SC", "South Carolina"),
          new CountrySubdivision(42L, UNITED_STATES, "US-SD", "South Dakota"),
          new CountrySubdivision(43L, UNITED_STATES, "US-TN", "Tennessee"),
          new CountrySubdivision(44L, UNITED_STATES, "US-TX", "Texas"),
          new CountrySubdivision(45L, UNITED_STATES, "US-UT", "Utah"),
          new CountrySubdivision(46L, UNITED_STATES, "US-VA", "Virginia"),
          new CountrySubdivision(47L, UNITED_STATES, "US-VT", "Vermont"),
          new CountrySubdivision(48L, UNITED_STATES, "US-WA", "Washington"),
          new CountrySubdivision(49L, UNITED_STATES, "US-WI", "Wisconsin"),
          new CountrySubdivision(50L, UNITED_STATES, "US-WV", "West Virginia"),
          new CountrySubdivision(51L, UNITED_STATES, "US-WY", "Wyoming"));

  @Test
  void containsExactlyFiftyOneEntriesWithRequiredValues() {
    Set<CountrySubdivision> actual =
        Arrays.stream(CountrySubdivisions.values())
            .map(CountrySubdivisions::value)
            .collect(Collectors.toSet());
    assertEquals(EXPECTED.size(), actual.size());
    assertEquals(EXPECTED.stream().collect(Collectors.toSet()), actual);
  }

  @Test
  void subdivisionIdsAreUnique() {
    long distinctIds =
        Arrays.stream(CountrySubdivisions.values())
            .map(CountrySubdivisions::value)
            .map(CountrySubdivision::countrySubdivisionId)
            .distinct()
            .count();
    assertEquals(EXPECTED.size(), distinctIds);
  }

  @Test
  void subdivisionCodesAreUnique() {
    long distinctCodes =
        Arrays.stream(CountrySubdivisions.values())
            .map(CountrySubdivisions::value)
            .map(CountrySubdivision::code)
            .distinct()
            .count();
    assertEquals(EXPECTED.size(), distinctCodes);
  }

  @Test
  void everySubdivisionBelongsToUnitedStatesWithUsCode() {
    for (CountrySubdivision subdivision : EXPECTED) {
      assertEquals(UNITED_STATES, subdivision.country());
      assertTrue(subdivision.code().startsWith("US-"));
    }
  }

  @Test
  void lookupReturnsEnumOwnedValueForEverySupportedCode() {
    for (CountrySubdivisions constant : CountrySubdivisions.values()) {
      String code = constant.value().code();
      Optional<CountrySubdivision> found = CountrySubdivisions.fromCode(code);
      assertTrue(found.isPresent());
      assertEquals(constant.value(), found.orElseThrow());
    }
  }

  @Test
  void lookupIsCaseSensitiveAndUntrimmed() {
    assertTrue(CountrySubdivisions.fromCode("US-CA").isPresent());
    assertFalse(CountrySubdivisions.fromCode("us-ca").isPresent());
    assertFalse(CountrySubdivisions.fromCode("US-CA ").isPresent());
    assertFalse(CountrySubdivisions.fromCode(" US-CA").isPresent());
    assertFalse(CountrySubdivisions.fromCode("").isPresent());
    assertFalse(CountrySubdivisions.fromCode("US-C").isPresent());
    assertFalse(CountrySubdivisions.fromCode("US-").isPresent());
    assertFalse(CountrySubdivisions.fromCode("XX-AB").isPresent());
  }

  @Test
  @SuppressWarnings("NullAway")
  void rejectsNullLookupArgument() {
    assertThrows(NullPointerException.class, () -> CountrySubdivisions.fromCode((String) null));
  }

  @Test
  void everyExpectedEntryResolvesToItsOwnValue() {
    for (CountrySubdivision expectedSubdivision : EXPECTED) {
      CountrySubdivision resolved =
          CountrySubdivisions.fromCode(expectedSubdivision.code()).orElseThrow();
      assertEquals(expectedSubdivision, resolved);
      assertEquals(expectedSubdivision.countrySubdivisionId(), resolved.countrySubdivisionId());
    }
  }
}
