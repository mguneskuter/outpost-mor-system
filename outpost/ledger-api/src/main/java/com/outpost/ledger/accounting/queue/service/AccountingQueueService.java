package com.outpost.ledger.accounting.queue.service;

import com.outpost.accounting.api.AccountingQueueErrorTypes;
import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.accounting.transactionlock.TransactionLock;
import com.outpost.accounting.transactionlock.repository.TransactionLockRepository;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.framework.logging.StructuredLogger;
import com.outpost.framework.queue.QueueFullException;
import com.outpost.framework.queue.TimeOrderedQueue;
import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

/** Accepts accounting requests: validates them, takes the transaction lock, and queues them. */
public final class AccountingQueueService {
  private static final StructuredLogger LOGGER =
      new StructuredLogger(LoggerFactory.getLogger(AccountingQueueService.class));
  private final TransactionLockRepository transactionLocks;
  private final TimeOrderedQueue<LockedAccountingQueueRequest> accountingQueue;
  private final Duration transactionLockLease;

  /** Creates a service that locks payments for {@code transactionLockLease} while booking. */
  public AccountingQueueService(
      TransactionLockRepository transactionLocks,
      TimeOrderedQueue<LockedAccountingQueueRequest> accountingQueue,
      Duration transactionLockLease) {
    this.transactionLocks = transactionLocks;
    this.accountingQueue = accountingQueue;
    this.transactionLockLease = transactionLockLease;
  }

  /**
   * Validates the request, takes the payment's transaction lock, and queues the request for
   * booking.
   *
   * @throws AccountingQueueRefusedException when a field the request's type requires is absent, a
   *     live transaction lock exists for the payment, or the queue holds its capacity, in which
   *     case the lock this call took is released first
   */
  public void accept(@Nullable AccountingQueueRequest request) {
    if (request == null
        || request.type() == null
        || blank(request.originalReference())
        || blank(request.merchantReference())
        || blank(request.pspCode())
        || blank(request.pspReference())) {
      throw new AccountingQueueRefusedException(AccountingQueueErrorTypes.INVALID_REQUEST, request);
    }
    boolean complete =
        switch (request.type()) {
          case ORDER_CREATED ->
              !blank(request.merchantCode())
                  && request.shopperCountry() != null
                  && request.netAmount() != null
                  && request.taxAmount() != null
                  && request.grossAmount() != null
                  && belongsToShopperCountry(request);
          case AUTHORISATION, CAPTURE -> request.success() != null;
          case REFUND -> request.success() != null && !blank(request.refundReference());
        };
    if (!complete) {
      throw new AccountingQueueRefusedException(AccountingQueueErrorTypes.INVALID_REQUEST, request);
    }
    TransactionLock lock =
        transactionLocks
            .insertTransactionLock(request.originalReference(), transactionLockLease)
            .orElseThrow(
                () ->
                    new AccountingQueueRefusedException(
                        AccountingQueueErrorTypes.TRANSACTION_LOCKED, request));
    try {
      accountingQueue.add(new LockedAccountingQueueRequest(request, lock));
      LOGGER.info("Accounting request accepted", request.logFields());
    } catch (QueueFullException full) {
      transactionLocks.deleteTransactionLock(lock);
      throw new AccountingQueueRefusedException(AccountingQueueErrorTypes.QUEUE_FULL, request);
    }
  }

  private static boolean belongsToShopperCountry(AccountingQueueRequest request) {
    CountrySubdivision subdivision = request.shopperCountrySubdivision();
    return subdivision == null
        || Objects.equals(subdivision.getCountry(), request.shopperCountry());
  }

  private static boolean blank(@Nullable String value) {
    return value == null || value.isBlank();
  }
}
