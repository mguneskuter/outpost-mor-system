package com.outpost.ledger.accountingrequest.service;

import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.accounting.transactionlock.TransactionLock;
import com.outpost.accounting.transactionlock.repository.TransactionLockRepository;
import com.outpost.framework.logging.StructuredLogger;
import com.outpost.framework.queue.QueueItemHandler;
import com.outpost.framework.queue.QueueItemResults;
import com.outpost.ledger.payment.service.CaptureService;
import com.outpost.ledger.payment.service.PaymentCreationService;
import com.outpost.ledger.payment.service.PaymentEventService;
import com.outpost.ledger.payment.service.RefundService;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

/** Books each accepted accounting request and releases its transaction lock. */
public final class AccountingQueueProcessor
    implements QueueItemHandler<LockedAccountingQueueRequest> {
  private static final StructuredLogger LOGGER =
      new StructuredLogger(LoggerFactory.getLogger(AccountingQueueProcessor.class));
  private static final String BOOKED = "Accounting request booked";

  private final PaymentCreationService paymentCreation;
  private final PaymentEventService paymentEvents;
  private final CaptureService captures;
  private final RefundService refunds;
  private final TransactionLockRepository transactionLocks;

  /** Creates a processor over the booking services, called through their Spring proxies. */
  public AccountingQueueProcessor(
      PaymentCreationService paymentCreation,
      PaymentEventService paymentEvents,
      CaptureService captures,
      RefundService refunds,
      TransactionLockRepository transactionLocks) {
    this.paymentCreation = paymentCreation;
    this.paymentEvents = paymentEvents;
    this.captures = captures;
    this.refunds = refunds;
    this.transactionLocks = transactionLocks;
  }

  /**
   * Books the request in one database transaction and releases its lock. A booking that fails is
   * logged and never retried, so the result is always {@link QueueItemResults#DONE}.
   */
  @Override
  public QueueItemResults handle(LockedAccountingQueueRequest item) {
    AccountingQueueRequest request = item.request();
    try {
      String outcome =
          switch (request.type()) {
            case ORDER_CREATED -> {
              paymentCreation.create(request);
              yield BOOKED;
            }
            case AUTHORISATION -> {
              paymentEvents.recordAuthorisation(
                  request.originalReference(), required(request.success()));
              yield BOOKED;
            }
            case CAPTURE -> {
              captures.capture(request.originalReference(), required(request.success()));
              yield BOOKED;
            }
            case REFUND -> {
              if (required(request.success())) {
                refunds.refund(request.originalReference(), required(request.refundReference()));
                yield BOOKED;
              }
              yield "Refund failed at the PSP";
            }
          };
      LOGGER.info(outcome, request.logFields());
    } catch (RuntimeException exception) {
      LOGGER.error("Accounting request not booked", exception, request.logFields());
    } finally {
      release(item.transactionLock(), request);
    }
    return QueueItemResults.DONE;
  }

  private void release(TransactionLock lock, AccountingQueueRequest request) {
    try {
      transactionLocks.deleteTransactionLock(lock);
    } catch (RuntimeException exception) {
      LOGGER.error("Transaction lock not released", exception, request.logFields());
    }
  }

  private static <T> T required(@Nullable T value) {
    if (value == null) {
      throw new IllegalStateException("Accepted request lacks a field its type requires");
    }
    return value;
  }
}
