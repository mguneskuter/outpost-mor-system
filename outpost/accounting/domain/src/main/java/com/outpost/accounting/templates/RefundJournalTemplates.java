package com.outpost.accounting.templates;

import com.outpost.account.AccountTypes;
import com.outpost.accounting.JournalEntry;
import com.outpost.accounting.JournalEntryLine;
import com.outpost.accounting.JournalEntryTypes;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.TransactionEvent;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionTypes;
import com.outpost.payment.common.Amount;
import java.time.Instant;
import java.util.Objects;

/** Builds the three-line REFUND entry that reverses a captured payment amount. */
public enum RefundJournalTemplates {
  /** The refund rule without a fee-revenue reversal. */
  REFUND;

  private static final JournalTemplateValidator VALIDATOR = new JournalTemplateValidator();

  /** Builds and validates a REFUND entry from persisted transaction snapshots. */
  public JournalEntry build(
      TransactionEvent sourceEvent,
      Register pspReceivableRegister,
      Register taxPayableRegister,
      Register merchantPayableRegister,
      Amount gross,
      Amount net,
      Amount tax,
      Instant bookedAndPosted) {
    Objects.requireNonNull(bookedAndPosted, "bookedAndPosted");
    VALIDATOR.requireSource(
        sourceEvent, TransactionEventTypes.REFUNDED.getValue(), TransactionTypes.REFUND.getValue());
    requireAmounts(sourceEvent, gross, net, tax);
    VALIDATOR.requireRegister(
        pspReceivableRegister,
        RegisterTypes.PSP_RECEIVABLE.getValue(),
        AccountTypes.PSP.getValue());
    VALIDATOR.requireRegister(
        taxPayableRegister,
        RegisterTypes.TAX_PAYABLE.getValue(),
        AccountTypes.TAX_AUTHORITY.getValue());
    VALIDATOR.requireRegister(
        merchantPayableRegister,
        RegisterTypes.MERCHANT_PAYABLE.getValue(),
        AccountTypes.MERCHANT.getValue());
    VALIDATOR.requireMerchantRegister(merchantPayableRegister, sourceEvent);

    JournalEntry entry =
        new JournalEntry(
            sourceEvent, JournalEntryTypes.REFUND.getValue(), bookedAndPosted, bookedAndPosted);
    addLine(entry, pspReceivableRegister, gross.negated());
    addLine(entry, taxPayableRegister, tax);
    addLine(entry, merchantPayableRegister, net);
    VALIDATOR.requireBalanced(entry);
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

  private static void addLine(JournalEntry entry, Register register, Amount amount) {
    entry.addLine(new JournalEntryLine(entry, register, amount));
  }
}
