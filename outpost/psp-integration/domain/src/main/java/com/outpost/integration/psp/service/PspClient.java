package com.outpost.integration.psp.service;

/** Provider-neutral payment service provider operations. */
public interface PspClient {
  /** Creates an order at the payment service provider. */
  CreateOrderResult createOrder(CreateOrderRequest request);

  /** Requests a refund. */
  RefundResult refund(RefundRequest request);

  /** Cancels an uncaptured order. */
  CancelResult cancel(CancelRequest request);
}
