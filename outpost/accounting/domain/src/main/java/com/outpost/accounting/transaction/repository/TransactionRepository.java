package com.outpost.accounting.transaction.repository;

import com.outpost.accounting.TransactionEventTypes.TransactionEventType;
import com.outpost.accounting.transaction.PaymentDetail;
import com.outpost.accounting.transaction.RefundDetail;
import com.outpost.accounting.transaction.Transaction;
import com.outpost.accounting.transaction.TransactionEvent;
import java.util.List;
import java.util.Optional;

/**
 * Stores payments, their capture and refund transactions, and their events. A write of several rows
 * commits or rolls back as one unit and joins the caller's transaction when there is one.
 */
public interface TransactionRepository {
  /**
   * Stores an unsaved payment transaction with its detail.
   *
   * @return the stored payment, or empty when a transaction already has the payment's reference
   */
  Optional<PaymentDetail> insertPaymentDetail(PaymentDetail paymentDetail);

  /** Finds the payment with this reference. */
  Optional<PaymentDetail> findPaymentDetailByReference(String reference);

  /**
   * Finds the payment with this reference and locks its transaction until the caller's transaction
   * ends, so the bookings of one payment run one at a time.
   */
  Optional<PaymentDetail> findPaymentDetailByReferenceForUpdate(String reference);

  /**
   * Stores an unsaved capture transaction whose parent is a stored payment.
   *
   * @return the stored transaction, or empty when a transaction already has its reference
   */
  Optional<Transaction> insertTransaction(Transaction transaction);

  /** Finds the events of a stored transaction in the order they were stored. */
  List<TransactionEvent> findTransactionEvents(Transaction transaction);

  /**
   * Stores an event on a stored transaction, dated by the database transaction that stores it.
   *
   * @return the stored event, or empty when the transaction already has an event of this type
   */
  Optional<TransactionEvent> insertTransactionEvent(
      Transaction transaction, TransactionEventType transactionEventType);

  /** Finds the event of a stored payment's capture; empty when the payment has no capture. */
  Optional<TransactionEvent> findCaptureTransactionEventByPayment(Transaction payment);

  /**
   * Stores an unsaved refund transaction, whose parent is a stored payment, with its detail.
   *
   * @return the stored refund, or empty when a transaction already has the refund's reference
   */
  Optional<RefundDetail> insertRefundDetail(RefundDetail refundDetail);

  /** Finds the refund with this reference. */
  Optional<RefundDetail> findRefundDetailByReference(String reference);

  /** Finds the refunds of a stored payment. */
  List<RefundDetail> findRefundDetailsByPayment(Transaction payment);
}
