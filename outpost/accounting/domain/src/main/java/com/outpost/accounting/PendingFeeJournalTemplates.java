package com.outpost.accounting;

import com.outpost.account.AccountTypes;
import com.outpost.accounting.JournalEntryTypes.JournalEntryType;
import com.outpost.accounting.RegisterTypes.RegisterType;
import com.outpost.accounting.TransactionEventTypes.TransactionEventType;
import com.outpost.accounting.TransactionTypes.TransactionType;
import com.outpost.payment.common.Amount;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Builds and validates the two-line pending-fee entries that track a payment's fee before it is
 * recognized as revenue: {@code FEE_PENDING} books the fee measured at payment creation, and {@code
 * FEE_RELEASE} reverses it when the payment resolves without recognizing that fee as revenue.
 *
 * <p>Each rule writes a merchant {@code PENDING_FEE} line and an opposite-signed platform {@code
 * PENDING_FEE} line, equal in magnitude to the fee, even when the fee is zero. {@code FEE_PENDING}
 * debits the merchant and credits the platform; {@code FEE_RELEASE} credits the merchant and debits
 * the platform, reversing it. Both lines always carry the source transaction's currency. A caller
 * supplies the already-resolved merchant and platform {@code PENDING_FEE} registers; this class
 * does not resolve accounts or registers itself.
 */
public enum PendingFeeJournalTemplates {
  /** Booked on the payment's {@code ORDER_CREATED} event. */
  FEE_PENDING(
      JournalEntryTypes.FEE_PENDING.getValue(),
      Map.of(TransactionEventTypes.ORDER_CREATED.getValue(), TransactionTypes.PAYMENT.getValue()),
      true),

  /**
   * Booked on the payment's {@code REFUSED} or {@code CANCELLED} event, or a CAPTURE transaction's
   * {@code CAPTURE_FAILED} event.
   */
  FEE_RELEASE(
      JournalEntryTypes.FEE_RELEASE.getValue(),
      Map.of(
          TransactionEventTypes.REFUSED.getValue(), TransactionTypes.PAYMENT.getValue(),
          TransactionEventTypes.CANCELLED.getValue(), TransactionTypes.PAYMENT.getValue(),
          TransactionEventTypes.CAPTURE_FAILED.getValue(), TransactionTypes.CAPTURE.getValue()),
      false);

  @SuppressWarnings("Immutable")
  private final JournalEntryType journalEntryType;

  @SuppressWarnings("Immutable")
  private final Map<TransactionEventType, TransactionType> sourceTransactionTypeByEvent;

  private final boolean merchantIsDebit;

  PendingFeeJournalTemplates(
      JournalEntryType journalEntryType,
      Map<TransactionEventType, TransactionType> sourceTransactionTypeByEvent,
      boolean merchantIsDebit) {
    this.journalEntryType = journalEntryType;
    this.sourceTransactionTypeByEvent = sourceTransactionTypeByEvent;
    this.merchantIsDebit = merchantIsDebit;
  }

  /**
   * Builds and validates the entry: exactly two {@code PENDING_FEE} lines, one for the payment's
   * merchant and one for the platform, opposite in sign and equal in magnitude to {@code fee}.
   *
   * @throws IllegalArgumentException if {@code sourceEvent} does not carry a compatible event type,
   *     its transaction is not the required transaction type for that event, either register has
   *     the wrong register or account type, the merchant register does not belong to the payment's
   *     merchant account, {@code fee}'s currency differs from the source transaction's currency, or
   *     {@code fee} is negative.
   */
  public JournalEntry build(
      long journalEntryId,
      long merchantLineId,
      long platformLineId,
      TransactionEvent sourceEvent,
      Register merchantPendingFeeRegister,
      Register platformPendingFeeRegister,
      Amount fee,
      Instant bookedAndPosted) {
    Objects.requireNonNull(sourceEvent, "sourceEvent");
    Objects.requireNonNull(merchantPendingFeeRegister, "merchantPendingFeeRegister");
    Objects.requireNonNull(platformPendingFeeRegister, "platformPendingFeeRegister");
    Objects.requireNonNull(fee, "fee");
    Objects.requireNonNull(bookedAndPosted, "bookedAndPosted");
    TransactionType requiredTransactionType =
        sourceTransactionTypeByEvent.get(sourceEvent.getTransactionEventType());
    if (requiredTransactionType == null) {
      throw new IllegalArgumentException(
          "source event must be one of "
              + sourceTransactionTypeByEvent.keySet()
              + ": "
              + sourceEvent.getTransactionEventType().getCode());
    }
    if (!requiredTransactionType.equals(sourceEvent.getTransaction().getTransactionType())) {
      throw new IllegalArgumentException(
          "source event "
              + sourceEvent.getTransactionEventType().getCode()
              + " must occur on a "
              + requiredTransactionType.getCode()
              + " transaction: "
              + sourceEvent.getTransaction().getTransactionType().getCode());
    }
    if (!fee.currency().equals(sourceEvent.getTransaction().getAmount().currency())) {
      throw new IllegalArgumentException(
          "fee currency must match the source transaction currency: " + fee);
    }
    if (fee.quantity() < 0) {
      throw new IllegalArgumentException("fee must not be negative: " + fee);
    }
    requireRegister(
        merchantPendingFeeRegister, RegisterTypes.PENDING_FEE.getValue(), "merchant register");
    if (!merchantPendingFeeRegister
        .getAccount()
        .equals(sourceEvent.getTransaction().getMerchantAccount())) {
      throw new IllegalArgumentException(
          "merchant register must belong to the payment's merchant account");
    }
    requireRegister(
        platformPendingFeeRegister, RegisterTypes.PENDING_FEE.getValue(), "platform register");
    if (!platformPendingFeeRegister
        .getAccount()
        .getAccountType()
        .equals(AccountTypes.PLATFORM.getValue())) {
      throw new IllegalArgumentException("platform register must belong to a PLATFORM account");
    }

    JournalEntry entry =
        new JournalEntry(
            journalEntryId, sourceEvent, journalEntryType, bookedAndPosted, bookedAndPosted);
    long merchantQuantity = merchantIsDebit ? fee.quantity() : -fee.quantity();
    entry.addLine(
        new JournalEntryLine(
            merchantLineId,
            entry,
            merchantPendingFeeRegister,
            new Amount(fee.currency(), merchantQuantity)));
    entry.addLine(
        new JournalEntryLine(
            platformLineId,
            entry,
            platformPendingFeeRegister,
            new Amount(fee.currency(), -merchantQuantity)));
    return entry;
  }

  private static void requireRegister(Register register, RegisterType expected, String label) {
    if (!register.getRegisterType().equals(expected)) {
      throw new IllegalArgumentException(label + " must have register type " + expected.getCode());
    }
  }
}
