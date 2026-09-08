package com.outpost.common.iso;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** The countries supported by Outpost, each owning exactly one {@link Country} value. */
public enum Countries {
  AUSTRIA(1L, "AT", "Austria"),
  BELGIUM(2L, "BE", "Belgium"),
  BULGARIA(3L, "BG", "Bulgaria"),
  CYPRUS(4L, "CY", "Cyprus"),
  CZECHIA(5L, "CZ", "Czechia"),
  GERMANY(6L, "DE", "Germany"),
  DENMARK(7L, "DK", "Denmark"),
  ESTONIA(8L, "EE", "Estonia"),
  SPAIN(9L, "ES", "Spain"),
  FINLAND(10L, "FI", "Finland"),
  FRANCE(11L, "FR", "France"),
  UNITED_KINGDOM(12L, "GB", "United Kingdom"),
  GREECE(13L, "GR", "Greece"),
  CROATIA(14L, "HR", "Croatia"),
  HUNGARY(15L, "HU", "Hungary"),
  IRELAND(16L, "IE", "Ireland"),
  ITALY(17L, "IT", "Italy"),
  LITHUANIA(18L, "LT", "Lithuania"),
  LUXEMBOURG(19L, "LU", "Luxembourg"),
  LATVIA(20L, "LV", "Latvia"),
  MALTA(21L, "MT", "Malta"),
  NETHERLANDS(22L, "NL", "Netherlands"),
  POLAND(23L, "PL", "Poland"),
  PORTUGAL(24L, "PT", "Portugal"),
  ROMANIA(25L, "RO", "Romania"),
  SWEDEN(26L, "SE", "Sweden"),
  SLOVENIA(27L, "SI", "Slovenia"),
  SLOVAKIA(28L, "SK", "Slovakia"),
  UNITED_STATES(29L, "US", "United States");

  private static final Map<String, Countries> BY_CODE =
      Arrays.stream(values())
          .collect(
              Collectors.toUnmodifiableMap(
                  constant -> constant.value().isoCode(), constant -> constant));

  private final Country value;

  Countries(long countryId, String isoCode, String name) {
    value = new Country(countryId, isoCode, name);
  }

  /** Returns the {@link Country} owned by this constant. */
  public Country value() {
    return value;
  }

  /** Returns the country for an exact, case-sensitive ISO code, if any. */
  public static Optional<Country> fromIsoCode(String isoCode) {
    Objects.requireNonNull(isoCode, "isoCode");
    return Optional.ofNullable(BY_CODE.get(isoCode)).map(Countries::value);
  }
}
