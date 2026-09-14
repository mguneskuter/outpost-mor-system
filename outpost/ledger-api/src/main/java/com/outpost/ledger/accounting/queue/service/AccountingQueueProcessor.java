package com.outpost.ledger.accounting.queue.service;

import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.accounting.transactionlock.TransactionLock;
import com.outpost.accounting.transactionlock.repository.TransactionLockRepository;
import com.outpost.framework.logging.StructuredLogger;
import com.outpost.framework.queue.QueueItemProcessor;
import com.outpost.framework.queue.QueueItemResults;
import com.outpost.ledger.payment.service.AuthorisationService;
import com.outpost.ledger.payment.service.CaptureService;
import com.outpost.ledger.payment.service.PaymentService;
import com.outpost.ledger.payment.service.RefundService;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

/** Books each accepted accounting request and releases its transaction lock. */
public final class AccountingQueueProcessor
    implements QueueItemProcessor<LockedAccountingQueueRequest> {
  private static final StructuredLogger LOGGER =
      new StructuredLogger(LoggerFactory.getLogger(AccountingQueueProcessor.class));
  private static final String BOOKED = "Accounting request booked";

  private final PaymentService paymentService;
  private final AuthorisationService authorisationService;
  private final CaptureService captureService;
  private final RefundService refundService;
  private final TransactionLockRepository transactionLocks;

  /** Creates a processor over the booking services, called through their Spring proxies. */
  public AccountingQueueProcessor(
      PaymentService paymentService,
      AuthorisationService authorisationService,
      CaptureService captureService,
      RefundService refundService,
      TransactionLockRepository transactionLocks) {
    this.paymentService = paymentService;
    this.authorisationService = authorisationService;
    this.captureService = captureService;
    this.refundService = refundService;
    this.transactionLocks = transactionLocks;
  }

  /**
   * Books the request in one database transaction and releases its lock. A booking that fails is
   * logged and never retried, so the result is always {@link QueueItemResults#DONE}.
   */
  @Override
  public QueueItemResults process(LockedAccountingQueueRequest item) {
    AccountingQueueRequest request = item.request();
    try {
      String logMessage =
          switch (request.type()) {
            case ORDER_CREATED -> {
              paymentService.bookPayment(request);
              yield BOOKED;
            }
            case AUTHORISATION -> {
              authorisationService.bookAuthorisation(
                  request.originalReference(), required(request.success()));
              yield BOOKED;
            }
            case CAPTURE -> {
              captureService.bookCapture(request.originalReference(), required(request.success()));
              yield BOOKED;
            }
            case REFUND -> {
              if (required(request.success())) {
                refundService.bookRefund(
                    request.originalReference(), required(request.refundReference()));
                yield BOOKED;
              }
              yield "Refund failed at the PSP";
            }
          };
      LOGGER.info(logMessage, request.logFields());
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
