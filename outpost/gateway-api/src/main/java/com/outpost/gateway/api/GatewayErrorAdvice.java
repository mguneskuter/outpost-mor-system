package com.outpost.gateway.api;

import com.outpost.framework.logging.LogFields;
import com.outpost.framework.logging.StructuredLogField;
import com.outpost.framework.logging.StructuredLogger;
import com.outpost.gateway.order.service.ModifyOrderException;
import com.outpost.gateway.order.service.OrderCreationException;
import com.outpost.gateway.report.service.BalanceReportException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Answers every controller failure with {@link ErrorResponse}. A controlled failure carries its own
 * status and code; an unexpected one is logged exactly once, at error, under the correlation
 * identifier the response carries.
 */
@RestControllerAdvice
public final class GatewayErrorAdvice {
  private static final StructuredLogger LOGGER =
      new StructuredLogger(LoggerFactory.getLogger(GatewayErrorAdvice.class));

  @ExceptionHandler(OrderCreationException.class)
  ResponseEntity<ErrorResponse> orderCreation(OrderCreationException exception) {
    return respond(exception.status(), exception.code());
  }

  @ExceptionHandler(ModifyOrderException.class)
  ResponseEntity<ErrorResponse> orderModification(ModifyOrderException exception) {
    return respond(exception.status(), exception.code());
  }

  @ExceptionHandler(BalanceReportException.class)
  ResponseEntity<ErrorResponse> balanceReport(BalanceReportException exception) {
    return respond(exception.status(), exception.code());
  }

  /** A request field failed its declared constraint; the constraint's message is the code. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> invalidField(MethodArgumentNotValidException exception) {
    String code =
        exception.getBindingResult().getAllErrors().stream()
            .map(ObjectError::getDefaultMessage)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(ErrorResponse.INVALID_REQUEST);
    return respond(HttpStatus.BAD_REQUEST.value(), code);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException exception) {
    LOGGER.warn("Request body could not be read", exception);
    return respond(HttpStatus.BAD_REQUEST.value(), ErrorResponse.INVALID_REQUEST);
  }

  @ExceptionHandler({
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ErrorResponse> invalidParameter(Exception exception) {
    return respond(HttpStatus.BAD_REQUEST.value(), ErrorResponse.INVALID_REQUEST);
  }

  @ExceptionHandler(RuntimeException.class)
  ResponseEntity<ErrorResponse> unexpected(RuntimeException exception) {
    String correlationId = UUID.randomUUID().toString();
    LOGGER.error(
        "Request failed",
        exception,
        new StructuredLogField(LogField.CORRELATION_ID, correlationId));
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse(ErrorResponse.INTERNAL_ERROR, correlationId));
  }

  private static ResponseEntity<ErrorResponse> respond(int status, String code) {
    return ResponseEntity.status(status).body(ErrorResponse.of(code));
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
