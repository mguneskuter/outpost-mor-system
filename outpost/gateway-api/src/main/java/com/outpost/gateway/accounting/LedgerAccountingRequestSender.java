package com.outpost.gateway.accounting;

import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.accounting.api.AccountingRequestApi;
import com.outpost.framework.logging.LogFields;
import com.outpost.framework.logging.StructuredLogField;
import com.outpost.framework.logging.StructuredLogger;
import com.outpost.framework.queue.QueueItemHandler;
import com.outpost.framework.queue.QueueItemResults;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

/** Delivers queued accounting requests to the Ledger. */
public final class LedgerAccountingRequestSender
    implements QueueItemHandler<AccountingQueueRequest> {
  private static final StructuredLogger LOGGER =
      new StructuredLogger(LoggerFactory.getLogger(LedgerAccountingRequestSender.class));

  private final AccountingRequestApi accountingRequestApi;

  /** Creates a sender over the Ledger's accounting request proxy. */
  public LedgerAccountingRequestSender(AccountingRequestApi accountingRequestApi) {
    this.accountingRequestApi = accountingRequestApi;
  }

  /**
   * Submits one request. A 409 from the Ledger, a 5xx, or a transport failure asks for a retry; any
   * other 4xx is final.
   */
  @Override
  public QueueItemResults handle(AccountingQueueRequest request) {
    try {
      accountingRequestApi.submit(request);
      LOGGER.info("accounting request delivered", fields(request));
      return QueueItemResults.DONE;
    } catch (HttpClientErrorException exception) {
      if (exception.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
        LOGGER.info("accounting request deferred", fields(request));
        return QueueItemResults.RETRY_LATER;
      }
      LOGGER.error("accounting request rejected by the Ledger", exception, fields(request));
      return QueueItemResults.DONE;
    } catch (RuntimeException exception) {
      LOGGER.warn("accounting request not delivered", exception, fields(request));
      return QueueItemResults.RETRY_LATER;
    }
  }

  private static StructuredLogField[] fields(AccountingQueueRequest request) {
    return new StructuredLogField[] {
      new StructuredLogField(LogField.REQUEST_TYPE, request.type().name()),
      new StructuredLogField(LogField.ORIGINAL_REFERENCE, request.originalReference())
    };
  }

  private enum LogField implements LogFields {
    REQUEST_TYPE("request_type"),
    ORIGINAL_REFERENCE("original_reference");

    private final String jsonKey;

    LogField(String jsonKey) {
      this.jsonKey = jsonKey;
    }

    @Override
    public String getJsonKey() {
      return jsonKey;
    }
  }
}
