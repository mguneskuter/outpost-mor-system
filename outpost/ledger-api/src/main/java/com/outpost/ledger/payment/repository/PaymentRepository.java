package com.outpost.ledger.payment.repository;

import com.outpost.account.Account;
import com.outpost.account.configuration.MerchantFeeConfiguration;
import com.outpost.accounting.Register;
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
  Account findTaxAuthorityAccountByCountryId(long countryId);

  /** Finds the platform account. */
  Account findPlatformAccount();

  /** Inserts a payment transaction, or returns null for a duplicate reference. */
  StoredTransaction insertTransaction(
      long merchantId, String reference, long gross, long currencyId);

  /** Inserts the payment detail. */
  void insertPaymentDetail(
      long transactionId, long countryId, Long subdivisionId, long pspId, long net, long tax);

  /** Inserts the order-created event, dated by the database transaction that stores it. */
  PaymentEvent insertEvent(long transactionId);

  /** Locks and returns the payment family root for a payment reference. */
  PaymentFamily findPaymentFamilyForUpdate(String reference);

  /** Reads payment events in their append order. */
  List<PaymentEvent> findPaymentEvents(long transactionId);

  /** Reads the payment's capture child and its capture event type, if one exists. */
  CaptureChild findCaptureChild(long paymentTransactionId);

  /** Reads a capture by its unique reference. */
  CaptureChild findCaptureByReference(String reference);

  /** Finds a register for an account and accounting purpose. */
  Register findRegister(long accountId, long registerTypeId);

  /** Inserts a CAPTURE child transaction, or returns null for a duplicate reference. */
  StoredTransaction insertCaptureTransaction(
      long paymentTransactionId,
      long merchantAccountId,
      String reference,
      long amount,
      long currencyId);

  /**
   * Inserts a lifecycle event dated by the database transaction that stores it, or returns null
   * when the transaction already has an event of that type.
   */
  PaymentEvent insertPaymentEvent(long transactionId, long eventTypeId);

  /** Reads the pending-fee lines that a refusal or cancellation must reverse. */
  PendingFee findPendingFee(long transactionId);

  /** Returns whether the payment has exactly one successful capture for its full amount. */
  boolean hasExactlyOneSuccessfulFullCapture(
      long paymentTransactionId, long gross, long currencyId);

  /** Reads the counterparties used by the successful CAPTURE entry. */
  CapturePosting findCapturePosting(long paymentTransactionId);

  /** Reads refund children and their latest lifecycle events. */
  List<RefundChild> findRefundChildren(long paymentTransactionId);

  /** Reads a refund by its unique reference. */
  RefundChild findRefundByReference(String reference);

  /** Inserts a REFUND child transaction, or returns null for a duplicate reference. */
  StoredTransaction insertRefundTransaction(
      long paymentTransactionId,
      long merchantAccountId,
      String reference,
      long gross,
      long currencyId);

  /** Inserts immutable refund detail. */
  void insertRefundDetail(long transactionId, long net, long tax);
}
