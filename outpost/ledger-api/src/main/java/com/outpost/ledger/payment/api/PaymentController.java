package com.outpost.ledger.payment.api;

import com.outpost.ledger.payment.service.CaptureException;
import com.outpost.ledger.payment.service.CaptureService;
import com.outpost.ledger.payment.service.PaymentCreationException;
import com.outpost.ledger.payment.service.PaymentCreationService;
import com.outpost.ledger.payment.service.PaymentEventCommand;
import com.outpost.ledger.payment.service.PaymentEventException;
import com.outpost.ledger.payment.service.PaymentEventService;
import com.outpost.ledger.payment.service.RefundReservationCommand;
import com.outpost.ledger.payment.service.RefundReservationException;
import com.outpost.ledger.payment.service.RefundReservationResult;
import com.outpost.ledger.payment.service.RefundReservationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP endpoint for payment creation. */
@RestController
@RequestMapping("/v1/payment")
public final class PaymentController {
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
  public ResponseEntity<PaymentResponse> create(
      @RequestBody CreatePaymentRequest request, HttpServletRequest ignored) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
  }

  /** Records a Worker-observed payment lifecycle event. */
  @PostMapping("/event")
  public ResponseEntity<Void> recordEvent(@RequestBody PaymentEventRequest request) {
    eventService.record(
        request == null
            ? null
            : new PaymentEventCommand(request.paymentReference(), request.event()));
    return ResponseEntity.noContent().build();
  }

  /** Records a Worker-observed capture outcome. */
  @PostMapping("/capture")
  public ResponseEntity<CaptureResponse> capture(@RequestBody CaptureRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(captureService.capture(request));
  }

  /** Reserves a refund amount observed by the Worker. */
  @PostMapping("/refund")
  public ResponseEntity<RefundResponse> refund(@RequestBody RefundRequest request) {
    RefundReservationCommand command =
        request == null
            ? null
            : new RefundReservationCommand(
                request.paymentReference(),
                request.refundReference(),
                request.netAmount(),
                request.taxAmount(),
                request.currency());
    RefundReservationResult result = refundReservationService.reserve(command);
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

  @ExceptionHandler(RefundReservationException.class)
  ResponseEntity<PaymentError> controlled(RefundReservationException exception) {
    return ResponseEntity.status(exception.status()).body(new PaymentError(exception.code()));
  }

  @ExceptionHandler({RuntimeException.class})
  ResponseEntity<PaymentError> unexpected(RuntimeException ignored) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new PaymentError("INTERNAL_ERROR"));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<PaymentError> malformed(HttpMessageNotReadableException ignored) {
    return ResponseEntity.badRequest().body(new PaymentError("INVALID_REQUEST"));
  }
}
