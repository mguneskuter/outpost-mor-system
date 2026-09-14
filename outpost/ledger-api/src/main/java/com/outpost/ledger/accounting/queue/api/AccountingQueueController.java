package com.outpost.ledger.accounting.queue.api;

import com.outpost.accounting.api.AccountingQueueApi;
import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.accounting.api.AccountingQueueResult;
import com.outpost.ledger.accounting.queue.service.AccountingQueueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Serves the accounting request route. */
@RestController
public final class AccountingQueueController implements AccountingQueueApi {
  private final AccountingQueueService service;

  /** Creates the controller over the accepting service. */
  public AccountingQueueController(AccountingQueueService service) {
    this.service = service;
  }

  @Override
  public ResponseEntity<AccountingQueueResult> submit(AccountingQueueRequest request) {
    service.accept(request);
    return ResponseEntity.accepted().body(AccountingQueueResult.accepted(request));
  }
}
