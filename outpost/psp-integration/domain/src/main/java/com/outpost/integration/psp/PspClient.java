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
   * Asks the PSP to refund the request's amount of the order. The PSP only acknowledges the call;
   * whether it refunded is reported by its {@code REFUND} event.
   *
   * @throws UnknownPspResultException when the PSP may or may not have accepted the refund
   */
  RefundPspOrderResult refund(RefundPspOrderRequest request);
}
