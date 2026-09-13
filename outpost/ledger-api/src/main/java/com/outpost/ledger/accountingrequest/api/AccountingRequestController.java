package com.outpost.ledger.accountingrequest.api;

import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.accounting.api.AccountingRequestApi;
import com.outpost.ledger.accountingrequest.service.AccountingRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Serves the accounting request route. */
@RestController
public final class AccountingRequestController implements AccountingRequestApi {
  private final AccountingRequestService service;

  /** Creates the controller over the accepting service. */
  public AccountingRequestController(AccountingRequestService service) {
    this.service = service;
  }

  @Override
  public ResponseEntity<Void> submit(AccountingQueueRequest request) {
    service.accept(request);
    return ResponseEntity.accepted().build();
  }
}
