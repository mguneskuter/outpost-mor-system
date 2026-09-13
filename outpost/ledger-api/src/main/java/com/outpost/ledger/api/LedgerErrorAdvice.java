package com.outpost.ledger.api;

import com.outpost.accounting.api.LedgerErrorResponse;
import com.outpost.framework.logging.LogFields;
import com.outpost.framework.logging.StructuredLogField;
import com.outpost.framework.logging.StructuredLogger;
import com.outpost.ledger.accountingrequest.service.InvalidAccountingRequestException;
import com.outpost.ledger.accountingrequest.service.TransactionLockedException;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Answers every controller failure with {@link LedgerErrorResponse}. A controlled failure carries
 * its status and code; an unexpected one is logged exactly once, at error, under the correlation
 * identifier the response carries.
 */
@RestControllerAdvice
public final class LedgerErrorAdvice {
  private static final StructuredLogger LOGGER =
      new StructuredLogger(LoggerFactory.getLogger(LedgerErrorAdvice.class));

  @ExceptionHandler(InvalidAccountingRequestException.class)
  ResponseEntity<LedgerErrorResponse> invalid(InvalidAccountingRequestException exception) {
    return respond(HttpStatus.BAD_REQUEST, LedgerErrorResponse.INVALID_REQUEST);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<LedgerErrorResponse> unreadable(HttpMessageNotReadableException exception) {
    LOGGER.warn("request body could not be read", exception);
    return respond(HttpStatus.BAD_REQUEST, LedgerErrorResponse.INVALID_REQUEST);
  }

  @ExceptionHandler(TransactionLockedException.class)
  ResponseEntity<LedgerErrorResponse> locked(TransactionLockedException exception) {
    return respond(HttpStatus.CONFLICT, LedgerErrorResponse.TRANSACTION_LOCKED);
  }

  @ExceptionHandler(RuntimeException.class)
  ResponseEntity<LedgerErrorResponse> unexpected(RuntimeException exception) {
    String correlationId = UUID.randomUUID().toString();
    LOGGER.error(
        "request failed",
        exception,
        new StructuredLogField(LogField.CORRELATION_ID, correlationId));
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new LedgerErrorResponse(LedgerErrorResponse.INTERNAL_ERROR, correlationId));
  }

  private static ResponseEntity<LedgerErrorResponse> respond(HttpStatus status, String code) {
    return ResponseEntity.status(status).body(LedgerErrorResponse.of(code));
  }

  private enum LogField implements LogFields {
    CORRELATION_ID("correlation_id");

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
