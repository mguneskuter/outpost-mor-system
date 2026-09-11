package com.outpost.accounting.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.accounting.AccountingFixtures;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CreatePaymentCommandTest {
  private static final Amount NET = new Amount(Currencies.EUR.getValue(), 80L);
  private static final Amount TAX = new Amount(Currencies.EUR.getValue(), 20L);

  @Test
  void acceptsValidCommandAndDerivesGross() {
    CreatePaymentCommand command =
        new CreatePaymentCommand(
            AccountingFixtures.merchant(),
            AccountingFixtures.psp(),
            "payment-1",
            NET,
            TAX,
            Countries.UNITED_STATES.getValue(),
            CountrySubdivisions.US_DE.getValue());

    assertEquals(new Amount(Currencies.EUR.getValue(), 100L), command.getGrossAmount());
    assertEquals(
        CountrySubdivisions.US_DE.getValue(), command.getShopperCountrySubdivision().orElseThrow());
  }

  @Test
  void acceptsMissingSubdivision() {
    CreatePaymentCommand command =
        new CreatePaymentCommand(
            AccountingFixtures.merchant(),
            AccountingFixtures.psp(),
            "payment-1",
            NET,
            TAX,
            Countries.GERMANY.getValue(),
            null);

    assertTrue(command.getShopperCountrySubdivision().isEmpty());
  }

  @Test
  void acceptsZeroNetOrTax() {
    new CreatePaymentCommand(
        AccountingFixtures.merchant(),
        AccountingFixtures.psp(),
        "payment-1",
        new Amount(Currencies.EUR.getValue(), 0L),
        new Amount(Currencies.EUR.getValue(), 100L),
        Countries.GERMANY.getValue(),
        null);
    new CreatePaymentCommand(
        AccountingFixtures.merchant(),
        AccountingFixtures.psp(),
        "payment-1",
        new Amount(Currencies.EUR.getValue(), 100L),
        new Amount(Currencies.EUR.getValue(), 0L),
        Countries.GERMANY.getValue(),
        null);
  }

  @Test
  void rejectsAnInactiveMerchantAccount() {
    Account root =
        Account.of(400L, AccountTypes.ROOT.getValue(), "R4", "Root", true, Instant.EPOCH, null);
    Account inactiveMerchant =
        Account.of(
            401L, AccountTypes.MERCHANT.getValue(), "M4", "Merchant", false, Instant.EPOCH, root);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CreatePaymentCommand(
                inactiveMerchant,
                AccountingFixtures.psp(),
                "payment-1",
                NET,
                TAX,
                Countries.GERMANY.getValue(),
                null));
  }

  @Test
  void rejectsAnInactivePspAccount() {
    Account root =
        Account.of(410L, AccountTypes.ROOT.getValue(), "R5", "Root", true, Instant.EPOCH, null);
    Account inactivePsp =
        Account.of(411L, AccountTypes.PSP.getValue(), "P5", "PSP", false, Instant.EPOCH, root);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CreatePaymentCommand(
                AccountingFixtures.merchant(),
                inactivePsp,
                "payment-1",
                NET,
                TAX,
                Countries.GERMANY.getValue(),
                null));
  }

  @Test
  void rejectsMerchantAccountOfTheWrongType() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CreatePaymentCommand(
                AccountingFixtures.psp(),
                AccountingFixtures.psp(),
                "payment-1",
                NET,
                TAX,
                Countries.GERMANY.getValue(),
                null));
  }

  @Test
  void rejectsPspAccountOfTheWrongType() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CreatePaymentCommand(
                AccountingFixtures.merchant(),
                AccountingFixtures.merchant(),
                "payment-1",
                NET,
                TAX,
                Countries.GERMANY.getValue(),
                null));
  }

  @Test
  void rejectsBlankReference() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CreatePaymentCommand(
                AccountingFixtures.merchant(),
                AccountingFixtures.psp(),
                "  ",
                NET,
                TAX,
                Countries.GERMANY.getValue(),
                null));
  }

  @Test
  void rejectsMismatchedNetAndTaxCurrency() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CreatePaymentCommand(
                AccountingFixtures.merchant(),
                AccountingFixtures.psp(),
                "payment-1",
                NET,
                new Amount(Currencies.USD.getValue(), 20L),
                Countries.GERMANY.getValue(),
                null));
  }

  @Test
  void rejectsNegativeNetOrTax() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CreatePaymentCommand(
                AccountingFixtures.merchant(),
                AccountingFixtures.psp(),
                "payment-1",
                new Amount(Currencies.EUR.getValue(), -1L),
                TAX,
                Countries.GERMANY.getValue(),
                null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CreatePaymentCommand(
                AccountingFixtures.merchant(),
                AccountingFixtures.psp(),
                "payment-1",
                NET,
                new Amount(Currencies.EUR.getValue(), -1L),
                Countries.GERMANY.getValue(),
                null));
  }

  @Test
  void rejectsZeroOrNegativeGross() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CreatePaymentCommand(
                AccountingFixtures.merchant(),
                AccountingFixtures.psp(),
                "payment-1",
                new Amount(Currencies.EUR.getValue(), 0L),
                new Amount(Currencies.EUR.getValue(), 0L),
                Countries.GERMANY.getValue(),
                null));
  }

  @Test
  void rejectsSubdivisionOutsideTheShopperCountry() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CreatePaymentCommand(
                AccountingFixtures.merchant(),
                AccountingFixtures.psp(),
                "payment-1",
                NET,
                TAX,
                Countries.GERMANY.getValue(),
                CountrySubdivisions.US_DE.getValue()));
  }
}
