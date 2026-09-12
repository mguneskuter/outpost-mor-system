package com.outpost.ledger.payment.repository;

import com.outpost.ledger.payment.repository.mybatis.AccountRow;
import com.outpost.ledger.payment.repository.mybatis.ExistingPaymentRow;
import com.outpost.ledger.payment.repository.mybatis.FeeRow;
import java.time.Instant;

/** Persistence operations required by payment creation. */
public interface PaymentRepository {
  /** Finds a committed payment by its reference. */
  ExistingPaymentRow findByReference(String reference);

  /** Finds an account by its stable code. */
  AccountRow findAccount(String code);

  /** Finds an account by id. */
  AccountRow findAccountById(long id);

  /** Finds a merchant fee configuration. */
  FeeRow findFee(long accountId, long currencyId);

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
}
