package com.outpost.accounting.payment;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.payment.common.Amount;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A validated request to create a payment, carrying no identity so it can be checked completely
 * before anything is persisted.
 */
public final class CreatePaymentCommand {
  private final Account merchantAccount;
  private final Account pspAccount;
  private final String reference;
  private final Amount netAmount;
  private final Amount taxAmount;
  private final Amount grossAmount;
  private final Country shopperCountry;
  @Nullable private final CountrySubdivision shopperCountrySubdivision;

  /** Creates and validates a payment-creation command. */
  public CreatePaymentCommand(
      Account merchantAccount,
      Account pspAccount,
      String reference,
      Amount netAmount,
      Amount taxAmount,
      Country shopperCountry,
      @Nullable CountrySubdivision shopperCountrySubdivision) {
    this.merchantAccount = Objects.requireNonNull(merchantAccount, "merchantAccount");
    if (!merchantAccount.getAccountType().equals(AccountTypes.MERCHANT.getValue())) {
      throw new IllegalArgumentException("merchantAccount must have the MERCHANT account type");
    }
    if (!merchantAccount.isActive()) {
      throw new IllegalArgumentException("merchantAccount must be active");
    }
    this.pspAccount = Objects.requireNonNull(pspAccount, "pspAccount");
    if (!pspAccount.getAccountType().equals(AccountTypes.PSP.getValue())) {
      throw new IllegalArgumentException("pspAccount must have the PSP account type");
    }
    if (!pspAccount.isActive()) {
      throw new IllegalArgumentException("pspAccount must be active");
    }
    if (reference == null || reference.isBlank()) {
      throw new IllegalArgumentException("reference must not be null or blank");
    }
    this.reference = reference;
    this.netAmount = Objects.requireNonNull(netAmount, "netAmount");
    this.taxAmount = Objects.requireNonNull(taxAmount, "taxAmount");
    if (!netAmount.currency().equals(taxAmount.currency())) {
      throw new IllegalArgumentException("net and tax amounts must use the same currency");
    }
    if (netAmount.quantity() < 0) {
      throw new IllegalArgumentException("netAmount must not be negative: " + netAmount);
    }
    if (taxAmount.quantity() < 0) {
      throw new IllegalArgumentException("taxAmount must not be negative: " + taxAmount);
    }
    this.grossAmount = netAmount.plus(taxAmount);
    if (grossAmount.quantity() <= 0) {
      throw new IllegalArgumentException("gross amount must be positive: " + grossAmount);
    }
    this.shopperCountry = Objects.requireNonNull(shopperCountry, "shopperCountry");
    if (shopperCountrySubdivision != null
        && !shopperCountrySubdivision.getCountry().equals(shopperCountry)) {
      throw new IllegalArgumentException("shopperCountrySubdivision must belong to shopperCountry");
    }
    this.shopperCountrySubdivision = shopperCountrySubdivision;
  }

  /** Returns the merchant account, active and of type MERCHANT. */
  public Account getMerchantAccount() {
    return merchantAccount;
  }

  /** Returns the PSP account, active and of type PSP. */
  public Account getPspAccount() {
    return pspAccount;
  }

  public String getReference() {
    return reference;
  }

  public Amount getNetAmount() {
    return netAmount;
  }

  public Amount getTaxAmount() {
    return taxAmount;
  }

  /** Returns the gross amount, {@code net + tax}. */
  public Amount getGrossAmount() {
    return grossAmount;
  }

  public Country getShopperCountry() {
    return shopperCountry;
  }

  public Optional<CountrySubdivision> getShopperCountrySubdivision() {
    return Optional.ofNullable(shopperCountrySubdivision);
  }
}
