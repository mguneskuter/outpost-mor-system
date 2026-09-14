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
import com.outpost.payment.common.Amount;
import java.util.List;
import java.util.Optional;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/** Books a PSP-confirmed refund of a captured payment. */
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
   * Books the refund confirmed by the PSP under {@code refundReference}: the REFUND transaction for
   * {@code netAmount} plus {@code taxAmount}, its refund detail, the REFUNDED event, and the REFUND
   * entry. A repeat of a booked refund with the same amounts writes nothing.
   *
   * @throws BookingException when the request is not booked; its code says why: {@code
   *     REFUND_EXCEEDS_CAPTURE} means the payment's refunds would exceed its captured net, tax, or
   *     gross, and {@code INVALID_REQUEST} that the amounts are not in the payment's currency
   */
  @Transactional
  public void bookRefund(
      String originalReference, String refundReference, Amount netAmount, Amount taxAmount) {
    PaymentDetail payment =
        transactions
            .findPaymentDetailByReferenceForUpdate(originalReference)
            .orElseThrow(() -> refused(BookingErrorCodes.PAYMENT_NOT_FOUND));
    Transaction paymentTransaction = payment.getPaymentTransaction();
    Optional<RefundDetail> bookedRefund = transactions.findRefundDetailByReference(refundReference);
    if (bookedRefund.isPresent()) {
      if (isRepeatOf(bookedRefund.get(), originalReference, netAmount, taxAmount)) {
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
    if (!netAmount.currency().equals(paymentTransaction.getAmount().currency())
        || !taxAmount.currency().equals(paymentTransaction.getAmount().currency())) {
      throw refused(BookingErrorCodes.INVALID_REQUEST);
    }
    // Cap the payment's refunds at its capture — net, tax, and gross each stay within what was
    // captured, so a merchant never refunds more than the shopper paid.
    if (exceedsCapture(payment, netAmount, taxAmount)) {
      throw refused(BookingErrorCodes.REFUND_EXCEEDS_CAPTURE);
    }
    RefundDetail refund =
        transactions
            .insertRefundDetail(requestedRefund(payment, refundReference, netAmount, taxAmount))
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
        "Refund booked",
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

  private boolean exceedsCapture(PaymentDetail payment, Amount netAmount, Amount taxAmount) {
    List<RefundDetail> bookedRefunds =
        transactions.findRefundDetailsByPayment(payment.getPaymentTransaction());
    long net = netAmount.quantity();
    long tax = taxAmount.quantity();
    long gross;
    try {
      gross = Math.addExact(net, tax);
      for (RefundDetail booked : bookedRefunds) {
        net = Math.addExact(net, booked.getNetAmount().quantity());
        tax = Math.addExact(tax, booked.getTaxAmount().quantity());
        gross = Math.addExact(gross, booked.getRefundTransaction().getAmount().quantity());
      }
    } catch (ArithmeticException overflow) {
      return true;
    }
    return net > payment.getNetAmount().quantity()
        || tax > payment.getTaxAmount().quantity()
        || gross > payment.getPaymentTransaction().getAmount().quantity();
  }

  /** Whether a booked refund is this request again: the same payment, net, and tax. */
  private static boolean isRepeatOf(
      RefundDetail refund, String originalReference, Amount netAmount, Amount taxAmount) {
    return refund
            .getRefundTransaction()
            .getParentTransaction()
            .filter(payment -> payment.getReference().equals(originalReference))
            .isPresent()
        && refund.getNetAmount().equals(netAmount)
        && refund.getTaxAmount().equals(taxAmount);
  }

  private static RefundDetail requestedRefund(
      PaymentDetail payment, String refundReference, Amount netAmount, Amount taxAmount) {
    Transaction paymentTransaction = payment.getPaymentTransaction();
    try {
      return new RefundDetail(
          Transaction.childOf(
              paymentTransaction,
              null,
              TransactionTypes.REFUND.getValue(),
              paymentTransaction.getMerchantAccount(),
              refundReference,
              netAmount.plus(taxAmount),
              null),
          netAmount,
          taxAmount);
    } catch (IllegalArgumentException | ArithmeticException exception) {
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
