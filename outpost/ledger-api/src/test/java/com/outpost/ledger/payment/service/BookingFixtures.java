package com.outpost.ledger.payment.service;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionTypes;
import com.outpost.accounting.transaction.PaymentDetail;
import com.outpost.accounting.transaction.Transaction;
import com.outpost.accounting.transaction.TransactionEvent;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import java.time.Instant;

/** The accounts, registers, and stored payment one booking scenario starts from. */
final class BookingFixtures {
  static final Instant NOW = Instant.parse("2026-09-13T10:00:00Z");
  static final String PAYMENT_REFERENCE = "payment-1";

  final Account root =
      Account.of(1L, AccountTypes.ROOT.getValue(), "ROOT", "Root", true, NOW, null);
  final Account platform =
      Account.of(100L, AccountTypes.PLATFORM.getValue(), "OUTPOST", "Outpost", true, NOW, root);
  final Account merchant =
      Account.of(200L, AccountTypes.MERCHANT.getValue(), "SHOP", "Shop", true, NOW, root);
  final Account otherMerchant =
      Account.of(210L, AccountTypes.MERCHANT.getValue(), "OTHER", "Other", true, NOW, root);
  final Account psp = Account.of(300L, AccountTypes.PSP.getValue(), "PSP", "PSP", true, NOW, root);
  final Account taxAuthority =
      Account.of(1006L, AccountTypes.TAX_AUTHORITY.getValue(), "TAX_DE", "Tax DE", true, NOW, root);
  final Register merchantPendingFee =
      new Register(20008L, merchant, RegisterTypes.PENDING_FEE.getValue());
  final Register platformPendingFee =
      new Register(10008L, platform, RegisterTypes.PENDING_FEE.getValue());

  /** A stored payment of EUR 120.00 gross, 100.00 net, and 20.00 tax to a German shopper. */
  PaymentDetail payment() {
    return new PaymentDetail(
        Transaction.of(
            10L,
            TransactionTypes.PAYMENT.getValue(),
            merchant,
            PAYMENT_REFERENCE,
            eur(12_000L),
            NOW),
        Countries.GERMANY.getValue(),
        null,
        psp,
        eur(10_000L),
        eur(2_000L));
  }

  /** A stored child transaction of {@code payment}. */
  Transaction child(
      Transaction payment, long transactionId, TransactionTypes type, String reference) {
    Transaction storedPayment =
        Transaction.of(
            payment.getTransactionId().orElseThrow(),
            payment.getTransactionType(),
            payment.getMerchantAccount(),
            payment.getReference(),
            payment.getAmount(),
            NOW);
    return Transaction.childOf(
        storedPayment,
        transactionId,
        type.getValue(),
        merchant,
        reference,
        payment.getAmount(),
        NOW);
  }

  static TransactionEvent event(long id, Transaction transaction, TransactionEventTypes type) {
    return new TransactionEvent(id, transaction, type.getValue(), NOW);
  }

  static Amount eur(long quantity) {
    return new Amount(Currencies.EUR.getValue(), quantity);
  }
}
