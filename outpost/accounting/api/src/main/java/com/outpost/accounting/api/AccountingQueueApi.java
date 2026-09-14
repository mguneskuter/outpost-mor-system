package com.outpost.accounting.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** The Ledger's accounting request route. */
@HttpExchange("/v1/accounting-request")
public interface AccountingQueueApi {
  /**
   * Submits one accounting request and answers an {@link AccountingQueueResult} for it: 202 when
   * the Ledger took the payment's transaction lock and queued the request; 400 INVALID_REQUEST when
   * a field the type requires is missing; 409 TRANSACTION_LOCKED when the lock is held; 503
   * QUEUE_FULL when the Ledger's queue holds its capacity.
   */
  @PostExchange
  ResponseEntity<AccountingQueueResult> submit(@RequestBody AccountingQueueRequest request);
}
