package com.outpost.accounting;

import com.outpost.account.AccountTypes;
import com.outpost.accounting.RegisterTypes.RegisterType;
import com.outpost.payment.common.Amount;
import java.time.Instant;
import java.util.Objects;

/** Builds the six-line CAPTURE entry that recognizes a successful payment capture. */
public enum CaptureJournalTemplates {
  /** The full-gross capture rule, including fee recognition and pending-fee release. */
  CAPTURE;

  /** Builds and validates a CAPTURE entry from explicit persisted snapshots. */
  public JournalEntry build(
      long journalEntryId,
      long pspLineId,
      long taxLineId,
      long merchantPayableLineId,
      long feeRevenueLineId,
      long merchantPendingFeeLineId,
      long platformPendingFeeLineId,
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
    Objects.requireNonNull(sourceEvent, "sourceEvent");
    Objects.requireNonNull(bookedAndPosted, "bookedAndPosted");
    if (!sourceEvent.getTransactionEventType().equals(TransactionEventTypes.CAPTURED.getValue())
        || !sourceEvent
            .getTransaction()
            .getTransactionType()
            .equals(TransactionTypes.CAPTURE.getValue())) {
      throw new IllegalArgumentException("CAPTURE requires CAPTURED on a CAPTURE transaction");
    }
    requireAmounts(sourceEvent, gross, net, tax, fee);
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
    requireRegister(
        feeRevenueRegister,
        RegisterTypes.FEE_REVENUE.getValue(),
        AccountTypes.PLATFORM.getValue(),
        "fee revenue register");
    requireRegister(
        merchantPendingFeeRegister,
        RegisterTypes.PENDING_FEE.getValue(),
        AccountTypes.MERCHANT.getValue(),
        "merchant pending-fee register");
    requireRegister(
        platformPendingFeeRegister,
        RegisterTypes.PENDING_FEE.getValue(),
        AccountTypes.PLATFORM.getValue(),
        "platform pending-fee register");
    if (!merchantPayableRegister
            .getAccount()
            .equals(sourceEvent.getTransaction().getMerchantAccount())
        || !merchantPendingFeeRegister
            .getAccount()
            .equals(sourceEvent.getTransaction().getMerchantAccount())) {
      throw new IllegalArgumentException("merchant registers must belong to the source merchant");
    }

    JournalEntry entry =
        new JournalEntry(
            journalEntryId,
            sourceEvent,
            JournalEntryTypes.CAPTURE.getValue(),
            bookedAndPosted,
            bookedAndPosted);
    long merchantProceeds = Math.subtractExact(net.quantity(), fee.quantity());
    addLine(entry, pspLineId, pspReceivableRegister, gross);
    addLine(entry, taxLineId, taxPayableRegister, tax.negated());
    addLine(
        entry,
        merchantPayableLineId,
        merchantPayableRegister,
        new Amount(gross.currency(), -merchantProceeds));
    addLine(entry, feeRevenueLineId, feeRevenueRegister, fee.negated());
    addLine(entry, merchantPendingFeeLineId, merchantPendingFeeRegister, fee.negated());
    addLine(entry, platformPendingFeeLineId, platformPendingFeeRegister, fee);
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
