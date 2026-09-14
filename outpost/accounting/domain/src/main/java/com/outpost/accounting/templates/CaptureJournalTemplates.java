package com.outpost.accounting.templates;

import com.outpost.account.AccountTypes;
import com.outpost.accounting.JournalEntryTypes;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionTypes;
import com.outpost.accounting.journalentry.CaptureRegisters;
import com.outpost.accounting.journalentry.JournalEntry;
import com.outpost.accounting.journalentry.JournalEntryLine;
import com.outpost.accounting.journalentry.PendingFee;
import com.outpost.accounting.transaction.PaymentDetail;
import com.outpost.accounting.transaction.TransactionEvent;
import com.outpost.payment.common.Amount;
import java.time.Instant;
import java.util.Objects;

/** Builds the six-line CAPTURE entry that recognizes a successful payment capture. */
public enum CaptureJournalTemplates {
  /** The full-gross capture rule, including fee recognition and pending-fee release. */
  CAPTURE;

  private static final JournalTemplateValidator VALIDATOR = new JournalTemplateValidator();

  /**
   * Builds and validates the CAPTURE entry of {@code sourceEvent}, the CAPTURED event of the
   * payment's capture: the gross to the PSP, the payment's tax to the tax authority, its net less
   * the pending fee to the merchant, and the pending fee released into fee revenue.
   *
   * @throws IllegalArgumentException if the event, the amounts, or a register does not fit the rule
   */
  public JournalEntry build(
      TransactionEvent sourceEvent,
      PaymentDetail payment,
      CaptureRegisters registers,
      PendingFee pendingFee,
      Instant bookedAndPosted) {
    Objects.requireNonNull(payment, "payment");
    Objects.requireNonNull(registers, "registers");
    Objects.requireNonNull(pendingFee, "pendingFee");
    Objects.requireNonNull(bookedAndPosted, "bookedAndPosted");
    VALIDATOR.requireSource(
        sourceEvent,
        TransactionEventTypes.CAPTURED.getValue(),
        TransactionTypes.CAPTURE.getValue());
    Amount gross = sourceEvent.getTransaction().getAmount();
    Amount net = payment.getNetAmount();
    Amount tax = payment.getTaxAmount();
    Amount fee = pendingFee.fee();
    requireAmounts(sourceEvent, gross, net, tax, fee);
    Register pspReceivableRegister = registers.pspReceivableRegister();
    Register taxPayableRegister = registers.taxPayableRegister();
    Register merchantPayableRegister = registers.merchantPayableRegister();
    Register feeRevenueRegister = registers.feeRevenueRegister();
    Register merchantPendingFeeRegister = pendingFee.merchantPendingFeeRegister();
    Register platformPendingFeeRegister = pendingFee.platformPendingFeeRegister();
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
    VALIDATOR.requireBalanced(entry);
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
