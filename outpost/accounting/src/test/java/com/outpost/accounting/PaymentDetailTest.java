package com.outpost.accounting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.outpost.common.iso.Countries;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import org.junit.jupiter.api.Test;

class PaymentDetailTest {
  @Test
  void acceptsPaymentDetailsForTheShopperCountryAndPsp() {
    PaymentDetail detail =
        new PaymentDetail(
            AccountingFixtures.payment(1L),
            Countries.UNITED_STATES.value(),
            CountrySubdivisions.US_DE.value(),
            AccountingFixtures.psp(),
            new Amount(Currencies.EUR.value(), 80L),
            new Amount(Currencies.EUR.value(), 20L));

    assertEquals(
        CountrySubdivisions.US_DE.value(), detail.shopperCountrySubdivision().orElseThrow());
  }

  @Test
  void rejectsDetailsThatCannotBelongToThePayment() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PaymentDetail(
                AccountingFixtures.capture(1L),
                Countries.UNITED_STATES.value(),
                null,
                AccountingFixtures.psp(),
                new Amount(Currencies.EUR.value(), 80L),
                new Amount(Currencies.EUR.value(), 20L)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PaymentDetail(
                AccountingFixtures.payment(2L),
                Countries.GERMANY.value(),
                CountrySubdivisions.US_DE.value(),
                AccountingFixtures.psp(),
                new Amount(Currencies.EUR.value(), 80L),
                new Amount(Currencies.EUR.value(), 20L)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PaymentDetail(
                AccountingFixtures.payment(3L),
                Countries.UNITED_STATES.value(),
                null,
                AccountingFixtures.merchant(),
                new Amount(Currencies.EUR.value(), 80L),
                new Amount(Currencies.EUR.value(), 20L)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PaymentDetail(
                AccountingFixtures.payment(4L),
                Countries.UNITED_STATES.value(),
                null,
                AccountingFixtures.psp(),
                new Amount(Currencies.USD.value(), 80L),
                new Amount(Currencies.USD.value(), 20L)));
  }
}
