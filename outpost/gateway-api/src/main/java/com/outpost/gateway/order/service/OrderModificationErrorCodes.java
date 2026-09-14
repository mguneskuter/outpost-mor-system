package com.outpost.gateway.order.service;

/** Why a request to modify an order is refused; each name is the error code the API answers. */
public enum OrderModificationErrorCodes {
  MERCHANT_REQUIRED,
  UNSUPPORTED_MODIFICATION_TYPE,
  ORDER_NOT_FOUND,
  ORDER_NOT_PAID,
  /** A named reference is not a line of the order. */
  ORDER_LINE_NOT_FOUND,
  DUPLICATE_ORDER_LINE_REFERENCE,
  /** A named line has a refund that has not failed. */
  ORDER_LINE_ALREADY_REFUNDED,
  /** Every line of the order has a refund that has not failed. */
  ORDER_ALREADY_REFUNDED,
  REFUND_REJECTED,
  /** The PSP's answer to the refund is unknown; the same request may be sent again. */
  PSP_RETRYABLE
}
