package com.outpost.ledger.payment.service;

import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionTypes;
import com.outpost.accounting.journalentry.CaptureRegisters;
import com.outpost.accounting.journalentry.JournalEntry;
import com.outpost.accounting.journalentry.repository.JournalEntryRepository;
import com.outpost.accounting.templates.RefundJournalTemplates;
import com.outpost.accounting.transaction.PaymentDetail;
import com.outpost.accounting.transaction.RefundDetail;
import com.outpost.accounting.transaction.Transaction;
import com.outpost.accounting.transaction.TransactionEvent;
import com.outpost.accounting.transaction.repository.TransactionRepository;
import com.outpost.framework.logging.LogFields;
import com.outpost.framework.logging.StructuredLogField;
import com.outpost.framework.logging.StructuredLogger;
import java.util.Optional;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/** Books a PSP-confirmed full refund of a captured payment. */
public class RefundService {
  private static final StructuredLogger LOGGER =
      new StructuredLogger(LoggerFactory.getLogger(RefundService.class));
  private final TransactionRepository transactions;
  private final JournalEntryRepository journalEntries;

  /** Creates a service over the repositories it books through. */
  public RefundService(TransactionRepository transactions, JournalEntryRepository journalEntries) {
    this.transactions = transactions;
    this.journalEntries = journalEntries;
  }

  /**
   * Books the full refund confirmed by the PSP under {@code refundReference}: the REFUND
   * transaction for the payment's gross, its refund detail for the payment's net and tax, the
   * REFUNDED event, and the REFUND entry. A repeat of a booked refund writes nothing.
   *
   * @throws BookingException when the request is not booked; its code says why
   */
  @Transactional
  public void bookRefund(String originalReference, String refundReference) {
    PaymentDetail payment =
        transactions
            .findPaymentDetailByReferenceForUpdate(originalReference)
            .orElseThrow(() -> refused(BookingErrorCodes.PAYMENT_NOT_FOUND));
    Transaction paymentTransaction = payment.getPaymentTransaction();
    Optional<RefundDetail> bookedRefund = transactions.findRefundDetailByReference(refundReference);
    if (bookedRefund.isPresent()) {
      if (isRefundOf(bookedRefund.get(), originalReference)) {
        LOGGER.info(
            "Refund already booked",
            new StructuredLogField(LogFields.ORIGINAL_REFERENCE, originalReference),
            new StructuredLogField(LogFields.REFUND_REFERENCE, refundReference));
        return;
      }
      throw refused(BookingErrorCodes.REFERENCE_CONFLICT);
    }
    if (!isCapturedInFull(paymentTransaction)) {
      throw refused(BookingErrorCodes.NOT_CAPTURED);
    }
    if (!transactions.findRefundDetailsByPayment(paymentTransaction).isEmpty()) {
      throw refused(BookingErrorCodes.ALREADY_REFUNDED);
    }
    RefundDetail refund =
        transactions
            .insertRefundDetail(requestedRefund(payment, refundReference))
            .orElseThrow(() -> refused(BookingErrorCodes.REFERENCE_CONFLICT));
    TransactionEvent refunded =
        transactions
            .insertTransactionEvent(
                refund.getRefundTransaction(), TransactionEventTypes.REFUNDED.getValue())
            .orElseThrow(() -> refused(BookingErrorCodes.INCONSISTENT_BOOKING));
    CaptureRegisters captureRegisters =
        journalEntries
            .findCaptureRegistersByPayment(paymentTransaction)
            .orElseThrow(() -> refused(BookingErrorCodes.INCONSISTENT_BOOKING));
    journalEntries.insertJournalEntry(refundEntry(refunded, refund, captureRegisters));
    LOGGER.info(
        "Refund booked: the capture's net and tax reversed",
        new StructuredLogField(LogFields.ORIGINAL_REFERENCE, originalReference),
        new StructuredLogField(LogFields.REFUND_REFERENCE, refundReference));
  }

  private boolean isCapturedInFull(Transaction payment) {
    return transactions
        .findCaptureTransactionEventByPayment(payment)
        .filter(
            event ->
                event.getTransactionEventType().equals(TransactionEventTypes.CAPTURED.getValue())
                    && event.getTransaction().getAmount().equals(payment.getAmount()))
        .isPresent();
  }

  private static boolean isRefundOf(RefundDetail refund, String originalReference) {
    return refund
        .getRefundTransaction()
        .getParentTransaction()
        .filter(payment -> payment.getReference().equals(originalReference))
        .isPresent();
  }

  private static RefundDetail requestedRefund(PaymentDetail payment, String refundReference) {
    Transaction paymentTransaction = payment.getPaymentTransaction();
    try {
      return new RefundDetail(
          Transaction.childOf(
              paymentTransaction,
              null,
              TransactionTypes.REFUND.getValue(),
              paymentTransaction.getMerchantAccount(),
              refundReference,
              paymentTransaction.getAmount(),
              null),
          payment.getNetAmount(),
          payment.getTaxAmount());
    } catch (IllegalArgumentException exception) {
      throw new BookingException(BookingErrorCodes.INVALID_REQUEST, exception);
    }
  }

  private static JournalEntry refundEntry(
      TransactionEvent refunded, RefundDetail refund, CaptureRegisters captureRegisters) {
    try {
      return RefundJournalTemplates.REFUND.build(
          refunded, refund, captureRegisters, refunded.getOccurredAt());
    } catch (IllegalArgumentException | ArithmeticException exception) {
      throw new BookingException(BookingErrorCodes.INCONSISTENT_BOOKING, exception);
    }
  }

  private static BookingException refused(BookingErrorCodes code) {
    return new BookingException(code, null);
  }
}
