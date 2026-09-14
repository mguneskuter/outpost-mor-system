package com.outpost.accounting.transaction;

import com.outpost.accounting.TransactionTypes;
import com.outpost.payment.common.Amount;
import java.util.Objects;

/** Details of a refund transaction. */
public final class RefundDetail {
  private final Transaction refundTransaction;
  private final Amount netAmount;
  private final Amount taxAmount;

  /** Creates details for a refund child of a payment. */
  public RefundDetail(Transaction refundTransaction, Amount netAmount, Amount taxAmount) {
    this.refundTransaction = Objects.requireNonNull(refundTransaction, "refundTransaction");
    if (!refundTransaction.getTransactionType().equals(TransactionTypes.REFUND.getValue())) {
      throw new IllegalArgumentException("refundTransaction must have type REFUND");
    }
    Transaction payment =
        refundTransaction
            .getParentTransaction()
            .orElseThrow(
                () -> new IllegalArgumentException("refundTransaction must have a payment parent"));
    if (!payment.getTransactionType().equals(TransactionTypes.PAYMENT.getValue())) {
      throw new IllegalArgumentException("refundTransaction parent must have type PAYMENT");
    }
    if (!refundTransaction.getMerchantAccount().equals(payment.getMerchantAccount())) {
      throw new IllegalArgumentException(
          "refundTransaction merchant account must equal its payment parent");
    }
    this.netAmount = Objects.requireNonNull(netAmount, "netAmount");
    this.taxAmount = Objects.requireNonNull(taxAmount, "taxAmount");
    if (!netAmount.currency().equals(taxAmount.currency())
        || !netAmount.currency().equals(refundTransaction.getAmount().currency())
        || !netAmount.currency().equals(payment.getAmount().currency())) {
      throw new IllegalArgumentException(
          "refund, payment, net, and tax amounts must use the same currency");
    }
    if (netAmount.quantity() < 0 || taxAmount.quantity() < 0) {
      throw new IllegalArgumentException("refund net and tax amounts must not be negative");
    }
    long gross;
    try {
      gross = Math.addExact(netAmount.quantity(), taxAmount.quantity());
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException(
          "refund gross amount is outside the supported range", exception);
    }
    if (gross <= 0 || gross != refundTransaction.getAmount().quantity()) {
      throw new IllegalArgumentException(
          "refund gross amount must equal net plus tax and be positive");
    }
  }

  /** Returns the refund transaction. */
  public Transaction getRefundTransaction() {
    return refundTransaction;
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
    if (!(other instanceof RefundDetail that)) {
      return false;
    }
    return refundTransaction.equals(that.refundTransaction)
        && netAmount.equals(that.netAmount)
        && taxAmount.equals(that.taxAmount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(refundTransaction, netAmount, taxAmount);
  }
}
