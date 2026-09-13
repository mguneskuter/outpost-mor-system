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

/** Builds the six-line CAPTURE entry that recognizes a successful payment capture. */
public enum CaptureJournalTemplates {
  /** The full-gross capture rule, including fee recognition and pending-fee release. */
  CAPTURE;

  private static final JournalTemplateValidator VALIDATOR = new JournalTemplateValidator();

  /** Builds and validates a CAPTURE entry from explicit persisted snapshots. */
  public JournalEntry build(
      TransactionEvent sourceEvent,
      Register pspReceivableRegister,
      Register taxPayableRegister,
      Register merchantPayableRegister,
      Register feeRevenueRegister,
      Register merchantPendingFeeRegister,
      Register platformPendingFeeRegister,
      Amount gross,
      Amount net,
      Amount tax,
      Amount fee,
      Instant bookedAndPosted) {
    Objects.requireNonNull(bookedAndPosted, "bookedAndPosted");
    VALIDATOR.requireSource(
        sourceEvent,
        TransactionEventTypes.CAPTURED.getValue(),
        TransactionTypes.CAPTURE.getValue());
    requireAmounts(sourceEvent, gross, net, tax, fee);
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
    VALIDATOR.requireRegister(
        feeRevenueRegister, RegisterTypes.FEE_REVENUE.getValue(), AccountTypes.PLATFORM.getValue());
    VALIDATOR.requireRegister(
        merchantPendingFeeRegister,
        RegisterTypes.PENDING_FEE.getValue(),
        AccountTypes.MERCHANT.getValue());
    VALIDATOR.requireRegister(
        platformPendingFeeRegister,
        RegisterTypes.PENDING_FEE.getValue(),
        AccountTypes.PLATFORM.getValue());
    VALIDATOR.requireMerchantRegister(merchantPayableRegister, sourceEvent);
    VALIDATOR.requireMerchantRegister(merchantPendingFeeRegister, sourceEvent);

    JournalEntry entry =
        new JournalEntry(
            sourceEvent, JournalEntryTypes.CAPTURE.getValue(), bookedAndPosted, bookedAndPosted);
    long merchantProceeds = Math.subtractExact(net.quantity(), fee.quantity());
    addLine(entry, pspReceivableRegister, gross);
    addLine(entry, taxPayableRegister, tax.negated());
    addLine(entry, merchantPayableRegister, new Amount(gross.currency(), -merchantProceeds));
    addLine(entry, feeRevenueRegister, fee.negated());
    addLine(entry, merchantPendingFeeRegister, fee.negated());
    addLine(entry, platformPendingFeeRegister, fee);
    return entry;
  }

  private static void requireAmounts(
      TransactionEvent sourceEvent, Amount gross, Amount net, Amount tax, Amount fee) {
    Objects.requireNonNull(gross, "gross");
    Objects.requireNonNull(net, "net");
    Objects.requireNonNull(tax, "tax");
    Objects.requireNonNull(fee, "fee");
    if (!gross.currency().equals(sourceEvent.getTransaction().getAmount().currency())
        || !net.currency().equals(gross.currency())
        || !tax.currency().equals(gross.currency())
        || !fee.currency().equals(gross.currency())
        || gross.quantity() <= 0
        || net.quantity() < 0
        || tax.quantity() < 0
        || fee.quantity() < 0
        || fee.quantity() > net.quantity()
        || gross.quantity() != Math.addExact(net.quantity(), tax.quantity())
        || gross.quantity() != sourceEvent.getTransaction().getAmount().quantity()) {
      throw new IllegalArgumentException("capture amounts do not satisfy the capture formula");
    }
  }

  private static void addLine(JournalEntry entry, Register register, Amount amount) {
    entry.addLine(new JournalEntryLine(entry, register, amount));
  }
}
