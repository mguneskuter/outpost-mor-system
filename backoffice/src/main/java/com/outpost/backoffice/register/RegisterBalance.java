package com.outpost.backoffice.register;

/**
 * The journal lines booked on one register in one currency, in minor units: the debits, the
 * credits, and their difference.
 */
public record RegisterBalance(
    String accountType,
    String accountCode,
    String accountName,
    String registerType,
    String currency,
    long debits,
    long credits) {
  /** The balance, debits less credits, positive on the debit side. */
  public long net() {
    return debits - credits;
  }
}
