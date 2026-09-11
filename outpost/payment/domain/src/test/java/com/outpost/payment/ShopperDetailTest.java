package com.outpost.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.outpost.common.iso.Countries;
import com.outpost.common.iso.CountrySubdivisions;
import org.junit.jupiter.api.Test;

class ShopperDetailTest {
  @Test
  void acceptsMissingSubdivisionAsNoSubdivisionJurisdiction() {
    ShopperDetail shopper =
        new ShopperDetail(
            1L, "jane@example.com", "Jane Doe", Countries.NETHERLANDS.getValue(), null, "1011AB");

    assertTrue(shopper.getCountrySubdivision().isEmpty());
    assertEquals("jane@example.com", shopper.getEmail());
  }

  @Test
  void rejectsSubdivisionThatDoesNotBelongToTheCountry() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ShopperDetail(
                1L,
                "jane@example.com",
                "Jane Doe",
                Countries.NETHERLANDS.getValue(),
                CountrySubdivisions.US_CA.getValue(),
                null));
  }

  @Test
  void acceptsMatchingCountryAndSubdivision() {
    ShopperDetail shopper = PaymentFixtures.shopperWithSubdivision();

    assertEquals(Countries.UNITED_STATES.getValue(), shopper.getCountry());
    assertEquals(
        CountrySubdivisions.US_CA.getValue(), shopper.getCountrySubdivision().orElseThrow());
  }

  @Test
  void rejectsBlankFullName() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ShopperDetail(
                1L, "jane@example.com", " ", Countries.NETHERLANDS.getValue(), null, null));
  }

  @Test
  @SuppressWarnings("NullAway")
  void rejectsNullEmail() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ShopperDetail(1L, null, "Jane Doe", Countries.NETHERLANDS.getValue(), null, null));
  }

  @Test
  void rejectsBlankEmail() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ShopperDetail(1L, " ", "Jane Doe", Countries.NETHERLANDS.getValue(), null, null));
  }
}
