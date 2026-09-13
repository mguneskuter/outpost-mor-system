package com.outpost.ledger.accountingrequest.api;

import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.accounting.api.AccountingRequestApi;
import com.outpost.accounting.api.AccountingRequestError;
import com.outpost.framework.logging.StructuredLogger;
import com.outpost.ledger.accountingrequest.service.AccountingRequestService;
import com.outpost.ledger.accountingrequest.service.InvalidAccountingRequestException;
import com.outpost.ledger.accountingrequest.service.TransactionLockedException;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

/** Serves the accounting request route. */
@RestController
public final class AccountingRequestController implements AccountingRequestApi {
  private static final StructuredLogger LOGGER =
      new StructuredLogger(LoggerFactory.getLogger(AccountingRequestController.class));
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

  @ExceptionHandler(InvalidAccountingRequestException.class)
  ResponseEntity<AccountingRequestError> invalid(InvalidAccountingRequestException exception) {
    return ResponseEntity.badRequest().body(new AccountingRequestError("INVALID_REQUEST"));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<AccountingRequestError> malformed(HttpMessageNotReadableException exception) {
    LOGGER.warn("accounting request could not be read", exception);
    return ResponseEntity.badRequest().body(new AccountingRequestError("INVALID_REQUEST"));
  }

  @ExceptionHandler(TransactionLockedException.class)
  ResponseEntity<AccountingRequestError> locked(TransactionLockedException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new AccountingRequestError("TRANSACTION_LOCKED"));
  }

  @ExceptionHandler(RuntimeException.class)
  ResponseEntity<AccountingRequestError> unexpected(RuntimeException exception) {
    LOGGER.warn("accounting request failed", exception);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new AccountingRequestError("INTERNAL_ERROR"));
  }
}
