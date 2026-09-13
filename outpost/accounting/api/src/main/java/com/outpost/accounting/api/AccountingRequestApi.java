package com.outpost.accounting.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** The Ledger's accounting request route. */
@HttpExchange("/v1/accounting-request")
public interface AccountingRequestApi {
  /**
   * Submits one accounting request. 202 when the Ledger took the payment's transaction lock and
   * queued the request; 409 with code TRANSACTION_LOCKED when the lock is held; 400 with code
   * INVALID_REQUEST when a field the type requires is missing.
   */
  @PostExchange
  ResponseEntity<Void> submit(@RequestBody AccountingQueueRequest request);
}
