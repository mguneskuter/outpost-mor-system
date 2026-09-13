package com.outpost.accounting.queue.repository.mybatis;

import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/** Persistence operations owned by the accounting request queue. */
@RegisteredMapper
public interface AccountingRequestQueueMapper {
  /** Finds an existing request for any supported idempotency key. */
  @Nullable AccountingRequestRow findExisting(
      @Param("typeId") long typeId,
      @Param("reference") String reference,
      @Param("accountId") long accountId,
      @Param("idempotencyKey") @Nullable String idempotencyKey,
      @Param("pspEventQueueId") @Nullable Long pspEventQueueId);

  /** Inserts a request and returns its generated identifier. */
  @Nullable Long insertRequest(NewAccountingRequestRow request);

  /** Inserts one request line. */
  int insertLine(
      @Param("queueId") long queueId,
      @Param("orderLineReference") String orderLineReference,
      @Param("amount") @Nullable Long amount);

  /** Loads a request and its payment transaction when one exists. */
  @Nullable AccountingRequestRow findById(long queueId);

  /** Loads the lines for a request. */
  List<AccountingRequestLineRow> findLines(long queueId);

  /**
   * Takes the oldest eligible request row while retaining its row lock; a payment lock is live
   * while its lease ends after the database transaction's time.
   */
  @Nullable AccountingRequestRow claimCandidate();

  /** Marks a request as in progress. */
  int markInProgress(@Param("queueId") long queueId);

  /**
   * Creates or takes over the lock for the request's payment, leased from the database
   * transaction's time for {@code leaseMicros} microseconds.
   */
  int takePaymentLock(
      @Param("transactionId") long transactionId,
      @Param("queueId") long queueId,
      @Param("leaseMicros") long leaseMicros);

  /** Marks an unknown-payment request as failed and done. */
  int markMissingPaymentFailed(@Param("queueId") long queueId);

  /** Records a terminal outcome. */
  int markDone(@Param("queueId") long queueId, @Param("resultId") long resultId);

  /** Releases the payment lock held by a request. */
  int deletePaymentLock(long queueId);
}
