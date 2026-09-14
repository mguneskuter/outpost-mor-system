package com.outpost.gateway.order.service;

/** Why a request to modify an order is refused; each name is the error code the API answers. */
public enum OrderModificationErrorCodes {
  MERCHANT_REQUIRED,
  UNSUPPORTED_MODIFICATION_TYPE,
  ORDER_NOT_FOUND,
  ORDER_NOT_PAID,
  REFUND_REJECTED,
  /** The PSP's answer to the refund is unknown; the same request may be sent again. */
  PSP_RETRYABLE
}
