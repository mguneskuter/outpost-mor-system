package com.outpost.gateway.accounting.queue.client;

import com.outpost.accounting.api.AccountingQueueApi;
import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.framework.logging.StructuredLogger;
import com.outpost.framework.queue.QueueItemProcessor;
import com.outpost.framework.queue.QueueItemResults;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

/** Delivers queued accounting requests to the Ledger. */
public final class AccountingQueueSender implements QueueItemProcessor<AccountingQueueRequest> {
  private static final StructuredLogger LOGGER =
      new StructuredLogger(LoggerFactory.getLogger(AccountingQueueSender.class));

  private final AccountingQueueApi accountingRequestApi;

  /** Creates a sender over the Ledger's accounting request proxy. */
  public AccountingQueueSender(AccountingQueueApi accountingRequestApi) {
    this.accountingRequestApi = accountingRequestApi;
  }

  /**
   * Submits one request. A 409 from the Ledger, a 5xx, or a transport failure asks for a retry; any
   * other 4xx is final.
   */
  @Override
  public QueueItemResults process(AccountingQueueRequest request) {
    try {
      accountingRequestApi.submit(request);
      LOGGER.info("Accounting request delivered", request.logFields());
      return QueueItemResults.DONE;
    } catch (HttpClientErrorException exception) {
      if (exception.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
        LOGGER.info("Accounting request deferred", request.logFields());
        return QueueItemResults.RETRY_LATER;
      }
      LOGGER.error("Accounting request rejected by the Ledger", exception, request.logFields());
      return QueueItemResults.DONE;
    } catch (RuntimeException exception) {
      LOGGER.warn("Accounting request not delivered", exception, request.logFields());
      return QueueItemResults.RETRY_LATER;
    }
  }
}
