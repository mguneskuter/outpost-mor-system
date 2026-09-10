package com.outpost.accounting;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.payment.common.Amount;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Details of a payment transaction. */
public final class PaymentDetail {
  private final Transaction paymentTransaction;
  private final Country shopperCountry;
  @Nullable private final CountrySubdivision shopperCountrySubdivision;
  private final Account pspAccount;
  private final Amount netAmount;
  private final Amount taxAmount;

  /** Creates payment details. */
  public PaymentDetail(
      Transaction paymentTransaction,
      Country shopperCountry,
      @Nullable CountrySubdivision shopperCountrySubdivision,
      Account pspAccount,
      Amount netAmount,
      Amount taxAmount) {
    this.paymentTransaction = Objects.requireNonNull(paymentTransaction, "paymentTransaction");
    if (!paymentTransaction.getTransactionType().equals(TransactionTypes.PAYMENT.getValue())) {
      throw new IllegalArgumentException("paymentTransaction must have type PAYMENT");
    }
    this.shopperCountry = Objects.requireNonNull(shopperCountry, "shopperCountry");
    if (shopperCountrySubdivision != null
        && !shopperCountrySubdivision.getCountry().equals(shopperCountry)) {
      throw new IllegalArgumentException("shopperCountrySubdivision must belong to shopperCountry");
    }
    this.shopperCountrySubdivision = shopperCountrySubdivision;
    this.pspAccount = Objects.requireNonNull(pspAccount, "pspAccount");
    if (!pspAccount.getAccountType().equals(AccountTypes.PSP.getValue())) {
      throw new IllegalArgumentException("pspAccount must have type PSP");
    }
    this.netAmount = Objects.requireNonNull(netAmount, "netAmount");
    this.taxAmount = Objects.requireNonNull(taxAmount, "taxAmount");
    if (!netAmount.currency().equals(taxAmount.currency())
        || !netAmount.currency().equals(paymentTransaction.getAmount().currency())) {
      throw new IllegalArgumentException(
          "payment, net, and tax amounts must use the same currency");
    }
  }

  /** Returns the payment transaction. */
  public Transaction getPaymentTransaction() {
    return paymentTransaction;
  }

  /** Returns the shopper country. */
  public Country getShopperCountry() {
    return shopperCountry;
  }

  /** Returns the optional shopper subdivision. */
  public Optional<CountrySubdivision> getShopperCountrySubdivision() {
    return Optional.ofNullable(shopperCountrySubdivision);
  }

  /** Returns the PSP account. */
  public Account getPspAccount() {
    return pspAccount;
  }

  /** Returns the net amount. */
  public Amount getNetAmount() {
    return netAmount;
  }

  /** Returns the tax amount. */
  public Amount getTaxAmount() {
    return taxAmount;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof PaymentDetail that)) {
      return false;
    }
    return Objects.equals(paymentTransaction, that.paymentTransaction)
        && Objects.equals(shopperCountry, that.shopperCountry)
        && Objects.equals(shopperCountrySubdivision, that.shopperCountrySubdivision)
        && Objects.equals(pspAccount, that.pspAccount)
        && Objects.equals(netAmount, that.netAmount)
        && Objects.equals(taxAmount, that.taxAmount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        paymentTransaction,
        shopperCountry,
        shopperCountrySubdivision,
        pspAccount,
        netAmount,
        taxAmount);
  }
}
