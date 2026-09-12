package com.outpost.ledger.payment.api;

import com.outpost.ledger.payment.service.PaymentCreationException;
import com.outpost.ledger.payment.service.PaymentCreationService;
import com.outpost.ledger.payment.service.PaymentEventCommand;
import com.outpost.ledger.payment.service.PaymentEventException;
import com.outpost.ledger.payment.service.PaymentEventService;
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

  /** Creates the controller. */
  public PaymentController(PaymentCreationService service, PaymentEventService eventService) {
    this.service = service;
    this.eventService = eventService;
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

  @ExceptionHandler(PaymentCreationException.class)
  ResponseEntity<PaymentError> controlled(PaymentCreationException exception) {
    return ResponseEntity.status(exception.status()).body(new PaymentError(exception.code()));
  }

  @ExceptionHandler(PaymentEventException.class)
  ResponseEntity<PaymentError> controlled(PaymentEventException exception) {
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
