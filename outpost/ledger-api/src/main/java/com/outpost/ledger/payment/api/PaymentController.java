package com.outpost.ledger.payment.api;

import com.outpost.framework.logging.StructuredLogger;
import com.outpost.ledger.payment.service.AppendPaymentEventCommand;
import com.outpost.ledger.payment.service.CaptureException;
import com.outpost.ledger.payment.service.CaptureService;
import com.outpost.ledger.payment.service.PaymentCreationException;
import com.outpost.ledger.payment.service.PaymentCreationService;
import com.outpost.ledger.payment.service.PaymentEventException;
import com.outpost.ledger.payment.service.PaymentEventService;
import com.outpost.ledger.payment.service.RefundReservationService;
import com.outpost.ledger.payment.service.ReserveRefundCommand;
import com.outpost.ledger.payment.service.ReserveRefundException;
import com.outpost.ledger.payment.service.ReserveRefundResult;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP endpoints for payment lifecycle commands. */
@RestController
@RequestMapping("/v1/payment")
public final class PaymentController {
  private static final StructuredLogger LOGGER =
      new StructuredLogger(LoggerFactory.getLogger(PaymentController.class));
  private final PaymentCreationService service;
  private final PaymentEventService eventService;
  private final CaptureService captureService;
  private final RefundReservationService refundReservationService;

  /** Creates the controller. */
  public PaymentController(
      PaymentCreationService service,
      PaymentEventService eventService,
      CaptureService captureService,
      RefundReservationService refundReservationService) {
    this.service = service;
    this.eventService = eventService;
    this.captureService = captureService;
    this.refundReservationService = refundReservationService;
  }

  /** Creates a payment. */
  @PostMapping
  public ResponseEntity<PaymentResponse> create(@RequestBody CreatePaymentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
  }

  /** Appends a Worker-observed payment lifecycle event. */
  @PostMapping("/event")
  public ResponseEntity<Void> appendPaymentEvent(@RequestBody PaymentEventRequest request) {
    eventService.appendPaymentEvent(
        request == null
            ? null
            : new AppendPaymentEventCommand(
                request.paymentReference(), request.refundReference(), request.event()));
    return ResponseEntity.noContent().build();
  }

  /** Stores a capture the Worker observed as succeeded or failed. */
  @PostMapping("/capture")
  public ResponseEntity<CaptureResponse> capture(@RequestBody CaptureRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(captureService.capture(request));
  }

  /** Reserves a refund amount observed by the Worker. */
  @PostMapping("/refund")
  public ResponseEntity<RefundResponse> refund(@RequestBody RefundRequest request) {
    ReserveRefundCommand command =
        request == null
            ? null
            : new ReserveRefundCommand(
                request.paymentReference(),
                request.refundReference(),
                request.netAmount(),
                request.taxAmount(),
                request.currency());
    ReserveRefundResult result = refundReservationService.reserve(command);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new RefundResponse(result.refundReference(), result.createdAt()));
  }

  @ExceptionHandler(PaymentCreationException.class)
  ResponseEntity<PaymentError> controlled(PaymentCreationException exception) {
    return ResponseEntity.status(exception.status()).body(new PaymentError(exception.code()));
  }

  @ExceptionHandler(PaymentEventException.class)
  ResponseEntity<PaymentError> controlled(PaymentEventException exception) {
    return ResponseEntity.status(exception.status()).body(new PaymentError(exception.code()));
  }

  @ExceptionHandler(CaptureException.class)
  ResponseEntity<PaymentError> controlled(CaptureException exception) {
    return ResponseEntity.status(exception.status()).body(new PaymentError(exception.code()));
  }

  @ExceptionHandler(ReserveRefundException.class)
  ResponseEntity<PaymentError> controlled(ReserveRefundException exception) {
    return ResponseEntity.status(exception.status()).body(new PaymentError(exception.code()));
  }

  @ExceptionHandler({RuntimeException.class})
  ResponseEntity<PaymentError> unexpected(RuntimeException exception) {
    LOGGER.warn("payment request failed", exception);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new PaymentError("INTERNAL_ERROR"));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<PaymentError> malformed(HttpMessageNotReadableException exception) {
    LOGGER.warn("payment request could not be read", exception);
    return ResponseEntity.badRequest().body(new PaymentError("INVALID_REQUEST"));
  }
}
