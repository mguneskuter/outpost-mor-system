package com.outpost.accounting;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import java.time.Instant;

final class AccountingFixtures {
  static final Instant CREATED = Instant.parse("2026-02-01T00:00:00Z");
  static final Amount EUR_100 = new Amount(Currencies.EUR.value(), 100L);

  private AccountingFixtures() {}

  static Account merchant() {
    Account root = Account.of(100L, AccountTypes.ROOT.value(), "ROOT", "Root", true, CREATED, null);
    return Account.of(101L, AccountTypes.MERCHANT.value(), "M", "Merchant", true, CREATED, root);
  }

  static Account psp() {
    Account root =
        Account.of(200L, AccountTypes.ROOT.value(), "ROOT2", "Root", true, CREATED, null);
    return Account.of(201L, AccountTypes.PSP.value(), "PSP", "PSP", true, CREATED, root);
  }

  static Transaction payment(long transactionId) {
    return new Transaction(
        transactionId,
        TransactionTypes.PAYMENT.value(),
        merchant(),
        "payment-" + transactionId,
        EUR_100,
        CREATED);
  }

  static Transaction capture(long transactionId) {
    return new Transaction(
        transactionId,
        TransactionTypes.CAPTURE.value(),
        merchant(),
        "capture-" + transactionId,
        EUR_100,
        CREATED);
  }

  static Transaction refund(long transactionId) {
    return new Transaction(
        transactionId,
        TransactionTypes.REFUND.value(),
        merchant(),
        "refund-" + transactionId,
        EUR_100,
        CREATED);
  }
}
