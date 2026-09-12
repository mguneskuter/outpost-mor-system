package com.outpost.ledger.payment.repository;

import com.outpost.account.Account;
import com.outpost.account.configuration.MerchantFeeConfiguration;
import com.outpost.accounting.Register;
import java.time.Instant;
import java.util.List;

/** Persistence operations required by payment lifecycle commands. */
public interface PaymentRepository {
  /** Finds a committed payment by its reference. */
  ExistingPayment findByReference(String reference);

  /** Finds an account by its stable code. */
  Account findAccount(String code);

  /** Finds an account by id. */
  Account findAccountById(long id);

  /** Finds a merchant fee configuration. */
  MerchantFeeConfiguration findFee(long accountId, long currencyId);

  /** Finds the active tax authority account for a country. */
  Long findTaxAuthority(long countryId);

  /** Finds the platform account. */
  Long findPlatform();

  /** Finds a pending-fee register for an account. */
  Long findPendingRegister(long accountId);

  /** Inserts a transaction and returns its generated id, or null for a duplicate reference. */
  Long insertTransaction(
      long merchantId, String reference, long gross, long currencyId, Instant createdAt);

  /** Inserts the payment detail. */
  void insertPaymentDetail(
      long transactionId, long countryId, Long subdivisionId, long pspId, long net, long tax);

  /** Inserts the order-created event. */
  long insertEvent(long transactionId, Instant at);

  /** Inserts the pending-fee journal entry. */
  long insertEntry(long eventId, Instant at);

  /** Inserts a journal line. */
  long insertLine(long entryId, long registerId, long currencyId, long quantity);

  /** Locks and returns the payment family root for a payment reference. */
  PaymentFamily findPaymentFamilyForUpdate(String reference);

  /** Reads payment events in their append order. */
  List<PaymentEvent> findPaymentEvents(long transactionId);

  /** Reads the payment's capture child and its outcome, if one exists. */
  CaptureChild findCaptureChild(long paymentTransactionId);

  /** Reads a capture by its unique reference. */
  CaptureChild findCaptureByReference(String reference);

  /** Finds a register for an account and accounting purpose. */
  Register findRegister(long accountId, long registerTypeId);

  /** Inserts a CAPTURE child transaction, or null for a duplicate reference. */
  Long insertCaptureTransaction(
      long paymentTransactionId,
      long merchantAccountId,
      String reference,
      long amount,
      long currencyId,
      Instant createdAt);

  /** Inserts a lifecycle event and returns its id, or null when it already exists. */
  Long insertPaymentEvent(long transactionId, long eventTypeId, Instant occurredAt);

  /** Reads the pending-fee lines that a refusal or cancellation must reverse. */
  PendingFee findPendingFee(long transactionId);

  /** Inserts a fee-release journal entry. */
  long insertFeeReleaseEntry(long eventId, long entryTypeId, Instant at);

  /** Returns whether the payment has exactly one successful capture for its full amount. */
  boolean hasExactlyOneSuccessfulFullCapture(
      long paymentTransactionId, long gross, long currencyId);

  /** Reads refund children and their latest lifecycle events. */
  List<RefundChild> findRefundChildren(long paymentTransactionId);

  /** Reads a refund by its unique reference. */
  RefundChild findRefundByReference(String reference);

  /** Inserts a REFUND child transaction, or null for a duplicate reference. */
  Long insertRefundTransaction(
      long paymentTransactionId,
      long merchantAccountId,
      String reference,
      long gross,
      long currencyId,
      Instant createdAt);

  /** Inserts immutable refund detail. */
  void insertRefundDetail(long transactionId, long net, long tax);
}
