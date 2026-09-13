package com.outpost.ledger.api;

import com.outpost.accounting.api.AccountingQueueResult;
import com.outpost.accounting.api.AccountingRequestErrorTypes;
import com.outpost.accounting.api.LedgerErrorResponse;
import com.outpost.accounting.report.InvalidReportPeriodException;
import com.outpost.framework.logging.LogFields;
import com.outpost.framework.logging.StructuredLogField;
import com.outpost.framework.logging.StructuredLogger;
import com.outpost.ledger.accountingrequest.service.AccountingRequestRefusedException;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Answers every controller failure with {@link LedgerErrorResponse}, except an accounting request
 * the Ledger refuses, which is answered with its {@link AccountingQueueResult}. A controlled
 * failure carries its status and code; an unexpected one is logged exactly once, at error, under
 * the correlation identifier the response carries.
 */
@RestControllerAdvice
public final class LedgerErrorAdvice {
  private static final StructuredLogger LOGGER =
      new StructuredLogger(LoggerFactory.getLogger(LedgerErrorAdvice.class));

  @ExceptionHandler(AccountingRequestRefusedException.class)
  ResponseEntity<AccountingQueueResult> refused(AccountingRequestRefusedException exception) {
    AccountingRequestErrorTypes error = exception.getError();
    HttpStatus status =
        switch (error) {
          case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
          case TRANSACTION_LOCKED -> HttpStatus.CONFLICT;
          case QUEUE_FULL -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    AccountingQueueResult result =
        exception
            .getRequest()
            .map(request -> AccountingQueueResult.refused(request, error))
            .orElseGet(() -> AccountingQueueResult.refused(error));
    return ResponseEntity.status(status).body(result);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<LedgerErrorResponse> unreadable(HttpMessageNotReadableException exception) {
    LOGGER.warn("Request body could not be read", exception);
    return invalidRequest();
  }

  @ExceptionHandler({
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class,
    InvalidReportPeriodException.class
  })
  ResponseEntity<LedgerErrorResponse> invalidParameter(Exception exception) {
    return invalidRequest();
  }

  private static ResponseEntity<LedgerErrorResponse> invalidRequest() {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(LedgerErrorResponse.of(LedgerErrorResponse.INVALID_REQUEST));
  }

  @ExceptionHandler(RuntimeException.class)
  ResponseEntity<LedgerErrorResponse> unexpected(RuntimeException exception) {
    String correlationId = UUID.randomUUID().toString();
    LOGGER.error(
        "Request failed",
        exception,
        new StructuredLogField(LogField.CORRELATION_ID, correlationId));
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new LedgerErrorResponse(LedgerErrorResponse.INTERNAL_ERROR, correlationId));
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
