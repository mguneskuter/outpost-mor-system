package com.outpost.accounting;

import com.outpost.account.AccountTypes;
import com.outpost.accounting.RegisterTypes.RegisterType;
import com.outpost.payment.common.Amount;
import java.time.Instant;
import java.util.Objects;

/** Builds the three-line REFUND entry that reverses a captured payment amount. */
public enum RefundJournalTemplates {
  /** The refund rule without a fee-revenue reversal. */
  REFUND;

  /** Builds and validates a REFUND entry from persisted transaction snapshots. */
  public JournalEntry build(
      long journalEntryId,
      long pspLineId,
      long taxLineId,
      long merchantPayableLineId,
      TransactionEvent sourceEvent,
      Register pspReceivableRegister,
      Register taxPayableRegister,
      Register merchantPayableRegister,
      Amount gross,
      Amount net,
      Amount tax,
      Instant bookedAndPosted) {
    Objects.requireNonNull(sourceEvent, "sourceEvent");
    Objects.requireNonNull(bookedAndPosted, "bookedAndPosted");
    if (!sourceEvent.getTransactionEventType().equals(TransactionEventTypes.REFUNDED.getValue())
        || !sourceEvent
            .getTransaction()
            .getTransactionType()
            .equals(TransactionTypes.REFUND.getValue())) {
      throw new IllegalArgumentException("REFUND requires REFUNDED on a REFUND transaction");
    }
    requireAmounts(sourceEvent, gross, net, tax);
    requireRegister(
        pspReceivableRegister,
        RegisterTypes.PSP_RECEIVABLE.getValue(),
        AccountTypes.PSP.getValue(),
        "PSP register");
    requireRegister(
        taxPayableRegister,
        RegisterTypes.TAX_PAYABLE.getValue(),
        AccountTypes.TAX_AUTHORITY.getValue(),
        "tax register");
    requireRegister(
        merchantPayableRegister,
        RegisterTypes.MERCHANT_PAYABLE.getValue(),
        AccountTypes.MERCHANT.getValue(),
        "merchant payable register");
    if (!merchantPayableRegister
        .getAccount()
        .equals(sourceEvent.getTransaction().getMerchantAccount())) {
      throw new IllegalArgumentException("merchant register must belong to the refund merchant");
    }

    JournalEntry entry =
        new JournalEntry(
            journalEntryId,
            sourceEvent,
            JournalEntryTypes.REFUND.getValue(),
            bookedAndPosted,
            bookedAndPosted);
    addLine(entry, pspLineId, pspReceivableRegister, gross.negated());
    addLine(entry, taxLineId, taxPayableRegister, tax);
    addLine(entry, merchantPayableLineId, merchantPayableRegister, net);
    return entry;
  }

  private static void requireAmounts(
      TransactionEvent sourceEvent, Amount gross, Amount net, Amount tax) {
    Objects.requireNonNull(gross, "gross");
    Objects.requireNonNull(net, "net");
    Objects.requireNonNull(tax, "tax");
    if (!gross.currency().equals(sourceEvent.getTransaction().getAmount().currency())
        || !net.currency().equals(gross.currency())
        || !tax.currency().equals(gross.currency())
        || gross.quantity() <= 0
        || net.quantity() < 0
        || tax.quantity() < 0
        || gross.quantity() != Math.addExact(net.quantity(), tax.quantity())
        || gross.quantity() != sourceEvent.getTransaction().getAmount().quantity()) {
      throw new IllegalArgumentException("refund amounts do not satisfy the refund formula");
    }
  }

  private static void requireRegister(
      Register register,
      RegisterType expectedType,
      AccountTypes.AccountType expectedAccountType,
      String label) {
    Objects.requireNonNull(register, label);
    if (!register.getRegisterType().equals(expectedType)
        || !register.getAccount().getAccountType().equals(expectedAccountType)) {
      throw new IllegalArgumentException(label + " has the wrong role");
    }
  }

  private static void addLine(JournalEntry entry, long lineId, Register register, Amount amount) {
    entry.addLine(new JournalEntryLine(lineId, entry, register, amount));
  }
}
