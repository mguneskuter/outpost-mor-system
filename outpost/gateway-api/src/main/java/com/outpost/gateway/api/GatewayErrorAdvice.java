package com.outpost.gateway.api;

import com.outpost.framework.logging.LogFields;
import com.outpost.framework.logging.StructuredLogField;
import com.outpost.framework.logging.StructuredLogger;
import com.outpost.gateway.order.service.CreateOrderErrorCodes;
import com.outpost.gateway.order.service.CreateOrderException;
import com.outpost.gateway.order.service.OrderModificationErrorCodes;
import com.outpost.gateway.order.service.OrderModificationException;
import com.outpost.gateway.report.service.BalanceReportErrorCodes;
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
 * Answers every controller failure with {@link ErrorResponse}. A refused request answers the status
 * its error code maps to; an unexpected failure is logged exactly once, at error, under the
 * correlation identifier the response carries.
 */
@RestControllerAdvice
public final class GatewayErrorAdvice {
  private static final StructuredLogger LOGGER =
      new StructuredLogger(LoggerFactory.getLogger(GatewayErrorAdvice.class));

  @ExceptionHandler(CreateOrderException.class)
  ResponseEntity<ErrorResponse> createOrderRefused(CreateOrderException exception) {
    return refuse(status(exception.code()), exception.code().name());
  }

  @ExceptionHandler(OrderModificationException.class)
  ResponseEntity<ErrorResponse> orderModificationRefused(OrderModificationException exception) {
    return refuse(status(exception.code()), exception.code().name());
  }

  @ExceptionHandler(BalanceReportException.class)
  ResponseEntity<ErrorResponse> balanceReportRefused(BalanceReportException exception) {
    return refuse(status(exception.code()), exception.code().name());
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
    return respond(HttpStatus.BAD_REQUEST, code);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException exception) {
    LOGGER.warn("Request body could not be read", exception);
    return respond(HttpStatus.BAD_REQUEST, ErrorResponse.INVALID_REQUEST);
  }

  @ExceptionHandler({
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ErrorResponse> invalidParameter(Exception exception) {
    return respond(HttpStatus.BAD_REQUEST, ErrorResponse.INVALID_REQUEST);
  }

  @ExceptionHandler(RuntimeException.class)
  ResponseEntity<ErrorResponse> unexpected(RuntimeException exception) {
    String correlationId = UUID.randomUUID().toString();
    LOGGER.error(
        "Request failed",
        exception,
        new StructuredLogField(LogFields.CORRELATION_ID, correlationId));
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse(ErrorResponse.INTERNAL_ERROR, correlationId));
  }

  private static HttpStatus status(CreateOrderErrorCodes code) {
    return switch (code) {
      case MERCHANT_REQUIRED -> HttpStatus.FORBIDDEN;
      case MERCHANT_NOT_FOUND -> HttpStatus.UNAUTHORIZED;
      case IDEMPOTENCY_CONFLICT -> HttpStatus.CONFLICT;
      case UNSUPPORTED_CURRENCY,
          INVALID_PRODUCT_TYPE,
          DUPLICATE_MERCHANT_LINE_REFERENCE,
          MIXED_CURRENCIES,
          TOTAL_AMOUNT_MISMATCH,
          AMOUNT_OVERFLOW ->
          HttpStatus.BAD_REQUEST;
      case INVALID_COUNTRY,
          INVALID_STATE,
          TAX_RATE_UNAVAILABLE,
          MISSING_TAX_AUTHORITY,
          MISSING_FEE_CONFIGURATION,
          PSP_UNAVAILABLE ->
          HttpStatus.UNPROCESSABLE_ENTITY;
      case PSP_RETRYABLE -> HttpStatus.SERVICE_UNAVAILABLE;
    };
  }

  private static HttpStatus status(OrderModificationErrorCodes code) {
    return switch (code) {
      case MERCHANT_REQUIRED -> HttpStatus.FORBIDDEN;
      case UNSUPPORTED_MODIFICATION_TYPE -> HttpStatus.BAD_REQUEST;
      case ORDER_NOT_FOUND -> HttpStatus.NOT_FOUND;
      case ORDER_NOT_PAID -> HttpStatus.CONFLICT;
      case REFUND_REJECTED -> HttpStatus.UNPROCESSABLE_ENTITY;
      case PSP_RETRYABLE -> HttpStatus.SERVICE_UNAVAILABLE;
    };
  }

  private static HttpStatus status(BalanceReportErrorCodes code) {
    return switch (code) {
      case INVALID_REPORT_PERIOD -> HttpStatus.BAD_REQUEST;
      case MERCHANT_NOT_FOUND -> HttpStatus.UNAUTHORIZED;
      case REPORT_NOT_FOUND -> HttpStatus.NOT_FOUND;
    };
  }

  private static ResponseEntity<ErrorResponse> refuse(HttpStatus status, String code) {
    LOGGER.info(
        "Request refused",
        new StructuredLogField(LogFields.STATUS, Integer.toString(status.value())),
        new StructuredLogField(LogFields.CODE, code));
    return respond(status, code);
  }

  private static ResponseEntity<ErrorResponse> respond(HttpStatus status, String code) {
    return ResponseEntity.status(status).body(ErrorResponse.of(code));
  }
}
