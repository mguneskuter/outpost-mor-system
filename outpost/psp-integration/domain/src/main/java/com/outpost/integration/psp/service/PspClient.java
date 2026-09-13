package com.outpost.integration.psp.service;

/** Provider-neutral payment service provider operations. */
public interface PspClient {
  /** Creates an order at the payment service provider. */
  CreateOrderResult createOrder(CreateOrderRequest request);

  /** Requests a full refund of an order. */
  RefundResult refund(RefundRequest request);
}
