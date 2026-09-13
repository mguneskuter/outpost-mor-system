package com.outpost.accounting.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** Ledger's payment lifecycle routes. */
@HttpExchange("/v1/payment")
public interface PaymentApi {
  /** Creates a payment. */
  @PostExchange
  ResponseEntity<PaymentResponse> create(@RequestBody CreatePaymentRequest request);

  /** Appends a payment or refund lifecycle event. */
  @PostExchange("/event")
  ResponseEntity<Void> appendPaymentEvent(@RequestBody PaymentEventRequest request);

  /** Stores a PSP capture that succeeded or failed. */
  @PostExchange("/capture")
  ResponseEntity<CaptureResponse> capture(@RequestBody CaptureRequest request);

  /** Reserves a refund amount. */
  @PostExchange("/refund")
  ResponseEntity<RefundResponse> refund(@RequestBody RefundRequest request);
}
