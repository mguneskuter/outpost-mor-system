package com.outpost.integration.psp;

/** Provider-neutral payment service provider operations. */
public interface PspClient {
  /**
   * Creates an order at the payment service provider.
   *
   * @throws UnknownPspResultException when the PSP may or may not have created the order
   */
  CreatePspOrderResult createOrder(CreatePspOrderRequest request);

  /**
   * Requests a full refund of an order.
   *
   * @throws UnknownPspResultException when the PSP may or may not have accepted the refund
   */
  RefundPspOrderResult refund(RefundPspOrderRequest request);
}
