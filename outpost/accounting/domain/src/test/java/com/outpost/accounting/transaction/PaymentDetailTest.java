package com.outpost.accounting.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.outpost.accounting.AccountingFixtures;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import org.junit.jupiter.api.Test;

class PaymentDetailTest {
  @Test
  void retainsAllPaymentDetailsAndOptionalSubdivision() {
    Transaction payment = AccountingFixtures.payment(1L);
    var shopperCountry = Countries.UNITED_STATES.getValue();
    var shopperSubdivision = CountrySubdivisions.US_DE.getValue();
    var pspAccount = AccountingFixtures.psp();
    var netAmount = new Amount(Currencies.EUR.getValue(), 80L);
    var taxAmount = new Amount(Currencies.EUR.getValue(), 20L);

    PaymentDetail detail =
        new PaymentDetail(
            payment, shopperCountry, shopperSubdivision, pspAccount, netAmount, taxAmount);

    assertEquals(payment, detail.getPaymentTransaction());
    assertEquals(shopperCountry, detail.getShopperCountry());
    assertEquals(shopperSubdivision, detail.getShopperCountrySubdivision().orElseThrow());
    assertEquals(pspAccount, detail.getPspAccount());
    assertEquals(netAmount, detail.getNetAmount());
    assertEquals(taxAmount, detail.getTaxAmount());
  }

  @Test
  void representsMissingShopperSubdivisionAsEmptyOptional() {
    PaymentDetail detail =
        new PaymentDetail(
            AccountingFixtures.payment(1L),
            Countries.UNITED_STATES.getValue(),
            null,
            AccountingFixtures.psp(),
            new Amount(Currencies.EUR.getValue(), 80L),
            new Amount(Currencies.EUR.getValue(), 20L));

    assertTrue(detail.getShopperCountrySubdivision().isEmpty());
  }

  @Test
  void usesAllStoredFieldsForEqualityAndHashCode() {
    PaymentDetail detail =
        new PaymentDetail(
            AccountingFixtures.payment(1L),
            Countries.UNITED_STATES.getValue(),
            CountrySubdivisions.US_DE.getValue(),
            AccountingFixtures.psp(),
            new Amount(Currencies.EUR.getValue(), 80L),
            new Amount(Currencies.EUR.getValue(), 20L));
    PaymentDetail equalDetail =
        new PaymentDetail(
            AccountingFixtures.payment(1L),
            Countries.UNITED_STATES.getValue(),
            CountrySubdivisions.US_DE.getValue(),
            AccountingFixtures.psp(),
            new Amount(Currencies.EUR.getValue(), 80L),
            new Amount(Currencies.EUR.getValue(), 20L));
    PaymentDetail differentSubdivision =
        new PaymentDetail(
            AccountingFixtures.payment(1L),
            Countries.UNITED_STATES.getValue(),
            CountrySubdivisions.US_CA.getValue(),
            AccountingFixtures.psp(),
            new Amount(Currencies.EUR.getValue(), 80L),
            new Amount(Currencies.EUR.getValue(), 20L));

    assertEquals(detail, equalDetail);
    assertEquals(detail.hashCode(), equalDetail.hashCode());
    assertNotEquals(detail, differentSubdivision);
    assertNotEquals(detail, null);
    assertNotEquals(detail, new Object());
  }

  @Test
  void rejectsDetailsThatCannotBelongToThePayment() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PaymentDetail(
                AccountingFixtures.capture(1L),
                Countries.UNITED_STATES.getValue(),
                null,
                AccountingFixtures.psp(),
                new Amount(Currencies.EUR.getValue(), 80L),
                new Amount(Currencies.EUR.getValue(), 20L)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PaymentDetail(
                AccountingFixtures.payment(2L),
                Countries.GERMANY.getValue(),
                CountrySubdivisions.US_DE.getValue(),
                AccountingFixtures.psp(),
                new Amount(Currencies.EUR.getValue(), 80L),
                new Amount(Currencies.EUR.getValue(), 20L)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PaymentDetail(
                AccountingFixtures.payment(3L),
                Countries.UNITED_STATES.getValue(),
                null,
                AccountingFixtures.merchant(),
                new Amount(Currencies.EUR.getValue(), 80L),
                new Amount(Currencies.EUR.getValue(), 20L)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PaymentDetail(
                AccountingFixtures.payment(4L),
                Countries.UNITED_STATES.getValue(),
                null,
                AccountingFixtures.psp(),
                new Amount(Currencies.USD.getValue(), 80L),
                new Amount(Currencies.USD.getValue(), 20L)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PaymentDetail(
                AccountingFixtures.payment(5L),
                Countries.UNITED_STATES.getValue(),
                null,
                AccountingFixtures.psp(),
                new Amount(Currencies.EUR.getValue(), 80L),
                new Amount(Currencies.USD.getValue(), 20L)));
  }
}
