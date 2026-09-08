package com.outpost.common.iso;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** The United States subdivisions supported by Outpost, each owning exactly one value. */
public enum CountrySubdivisions {
  US_AK(1L, "US-AK", "Alaska"),
  US_AL(2L, "US-AL", "Alabama"),
  US_AR(3L, "US-AR", "Arkansas"),
  US_AZ(4L, "US-AZ", "Arizona"),
  US_CA(5L, "US-CA", "California"),
  US_CO(6L, "US-CO", "Colorado"),
  US_CT(7L, "US-CT", "Connecticut"),
  US_DC(8L, "US-DC", "District of Columbia"),
  US_DE(9L, "US-DE", "Delaware"),
  US_FL(10L, "US-FL", "Florida"),
  US_GA(11L, "US-GA", "Georgia"),
  US_HI(12L, "US-HI", "Hawaii"),
  US_IA(13L, "US-IA", "Iowa"),
  US_ID(14L, "US-ID", "Idaho"),
  US_IL(15L, "US-IL", "Illinois"),
  US_IN(16L, "US-IN", "Indiana"),
  US_KS(17L, "US-KS", "Kansas"),
  US_KY(18L, "US-KY", "Kentucky"),
  US_LA(19L, "US-LA", "Louisiana"),
  US_MA(20L, "US-MA", "Massachusetts"),
  US_MD(21L, "US-MD", "Maryland"),
  US_ME(22L, "US-ME", "Maine"),
  US_MI(23L, "US-MI", "Michigan"),
  US_MN(24L, "US-MN", "Minnesota"),
  US_MO(25L, "US-MO", "Missouri"),
  US_MS(26L, "US-MS", "Mississippi"),
  US_MT(27L, "US-MT", "Montana"),
  US_NC(28L, "US-NC", "North Carolina"),
  US_ND(29L, "US-ND", "North Dakota"),
  US_NE(30L, "US-NE", "Nebraska"),
  US_NH(31L, "US-NH", "New Hampshire"),
  US_NJ(32L, "US-NJ", "New Jersey"),
  US_NM(33L, "US-NM", "New Mexico"),
  US_NV(34L, "US-NV", "Nevada"),
  US_NY(35L, "US-NY", "New York"),
  US_OH(36L, "US-OH", "Ohio"),
  US_OK(37L, "US-OK", "Oklahoma"),
  US_OR(38L, "US-OR", "Oregon"),
  US_PA(39L, "US-PA", "Pennsylvania"),
  US_RI(40L, "US-RI", "Rhode Island"),
  US_SC(41L, "US-SC", "South Carolina"),
  US_SD(42L, "US-SD", "South Dakota"),
  US_TN(43L, "US-TN", "Tennessee"),
  US_TX(44L, "US-TX", "Texas"),
  US_UT(45L, "US-UT", "Utah"),
  US_VA(46L, "US-VA", "Virginia"),
  US_VT(47L, "US-VT", "Vermont"),
  US_WA(48L, "US-WA", "Washington"),
  US_WI(49L, "US-WI", "Wisconsin"),
  US_WV(50L, "US-WV", "West Virginia"),
  US_WY(51L, "US-WY", "Wyoming");

  private static final Map<String, CountrySubdivisions> BY_CODE =
      Arrays.stream(values())
          .collect(
              Collectors.toUnmodifiableMap(
                  constant -> constant.value().code(), constant -> constant));

  @SuppressWarnings("Immutable")
  private final CountrySubdivision value;

  CountrySubdivisions(long countrySubdivisionId, String code, String name) {
    value =
        new CountrySubdivision(countrySubdivisionId, Countries.UNITED_STATES.value(), code, name);
  }

  /** Returns the {@link CountrySubdivision} owned by this constant. */
  public CountrySubdivision value() {
    return value;
  }

  /** Returns the subdivision for an exact, case-sensitive code, if any. */
  public static Optional<CountrySubdivision> fromCode(String code) {
    Objects.requireNonNull(code, "code");
    return Optional.ofNullable(BY_CODE.get(code)).map(CountrySubdivisions::value);
  }

  /** Immutable subdivision value owned by one {@link CountrySubdivisions} constant. */
  public static final class CountrySubdivision {

    private static final Pattern CODE = Pattern.compile("[A-Z]{2}-[A-Z0-9]+");

    private final long countrySubdivisionId;
    private final Countries.Country country;
    private final String code;
    private final String name;

    private CountrySubdivision(
        long countrySubdivisionId, Countries.Country country, String code, String name) {
      if (countrySubdivisionId <= 0) {
        throw new IllegalArgumentException(
            "countrySubdivisionId must be positive: " + countrySubdivisionId);
      }
      if (country == null) {
        throw new IllegalArgumentException("country must not be null");
      }
      if (code == null
          || code.isBlank()
          || !CODE.matcher(code).matches()
          || !code.startsWith(country.isoCode())) {
        throw new IllegalArgumentException(
            "code must be an uppercase ISO 3166-2 shape for its country: " + code);
      }
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("name must not be null or blank");
      }
      this.countrySubdivisionId = countrySubdivisionId;
      this.country = country;
      this.code = code;
      this.name = name;
    }

    /** Returns the stable subdivision identifier. */
    public long countrySubdivisionId() {
      return countrySubdivisionId;
    }

    /** Returns the owning country. */
    public Countries.Country country() {
      return country;
    }

    /** Returns the exact ISO 3166-2 code. */
    public String code() {
      return code;
    }

    /** Returns the exact subdivision name. */
    public String name() {
      return name;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof CountrySubdivision that)) {
        return false;
      }
      return countrySubdivisionId == that.countrySubdivisionId
          && country.equals(that.country)
          && code.equals(that.code)
          && name.equals(that.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(countrySubdivisionId, country, code, name);
    }

    @Override
    public String toString() {
      return "CountrySubdivision{countrySubdivisionId="
          + countrySubdivisionId
          + ", country="
          + country
          + ", code="
          + code
          + ", name="
          + name
          + '}';
    }
  }
}
