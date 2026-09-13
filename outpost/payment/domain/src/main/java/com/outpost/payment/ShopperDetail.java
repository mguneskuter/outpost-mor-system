package com.outpost.payment;

import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;

/** The shopper a payment order was created for. */
public final class ShopperDetail {
  @Nullable private final Long shopperId;
  private final String email;
  private final String fullName;
  private final Country country;
  @Nullable private final CountrySubdivision countrySubdivision;
  @Nullable private final String postalCode;

  /**
   * Creates a shopper detail. A null {@code countrySubdivision} means the country has no
   * subdivision-level tax jurisdiction. {@code shopperId} is absent until the shopper is stored.
   */
  public ShopperDetail(
      @Nullable Long shopperId,
      String email,
      String fullName,
      Country country,
      @Nullable CountrySubdivision countrySubdivision,
      @Nullable String postalCode) {
    if (shopperId != null && shopperId <= 0) {
      throw new IllegalArgumentException("shopperId must be positive: " + shopperId);
    }
    this.shopperId = shopperId;
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("email must not be null or blank");
    }
    this.email = email;
    if (fullName == null || fullName.isBlank()) {
      throw new IllegalArgumentException("fullName must not be null or blank");
    }
    this.fullName = fullName;
    this.country = Objects.requireNonNull(country, "country");
    if (countrySubdivision != null && !countrySubdivision.getCountry().equals(country)) {
      throw new IllegalArgumentException("countrySubdivision must belong to country");
    }
    this.countrySubdivision = countrySubdivision;
    if (postalCode != null && postalCode.isBlank()) {
      throw new IllegalArgumentException("postalCode must not be blank when supplied");
    }
    this.postalCode = postalCode;
  }

  /** Returns the shopper identity; empty until the shopper is stored. */
  public OptionalLong getShopperId() {
    return shopperId == null ? OptionalLong.empty() : OptionalLong.of(shopperId);
  }

  /** Returns the shopper's email, the shopper's natural key. */
  public String getEmail() {
    return email;
  }

  /** Returns the shopper's full name. */
  public String getFullName() {
    return fullName;
  }

  /** Returns the shopper's country. */
  public Country getCountry() {
    return country;
  }

  /** Returns the shopper's subdivision; empty means no subdivision-level jurisdiction. */
  public Optional<CountrySubdivision> getCountrySubdivision() {
    return Optional.ofNullable(countrySubdivision);
  }

  /** Returns the shopper's postal code, if supplied. */
  public Optional<String> getPostalCode() {
    return Optional.ofNullable(postalCode);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ShopperDetail that)) {
      return false;
    }
    return Objects.equals(shopperId, that.shopperId)
        && Objects.equals(email, that.email)
        && Objects.equals(fullName, that.fullName)
        && Objects.equals(country, that.country)
        && Objects.equals(countrySubdivision, that.countrySubdivision)
        && Objects.equals(postalCode, that.postalCode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(shopperId, email, fullName, country, countrySubdivision, postalCode);
  }
}
