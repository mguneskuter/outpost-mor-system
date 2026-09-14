package com.outpost.gateway.order.service;

/** Why a request to create an order is refused; each name is the error code the API answers. */
public enum CreateOrderErrorCodes {
  MERCHANT_REQUIRED,
  MERCHANT_NOT_FOUND,
  IDEMPOTENCY_CONFLICT,
  INVALID_COUNTRY,
  INVALID_STATE,
  UNSUPPORTED_CURRENCY,
  INVALID_PRODUCT_TYPE,
  DUPLICATE_MERCHANT_LINE_REFERENCE,
  MIXED_CURRENCIES,
  TOTAL_AMOUNT_MISMATCH,
  AMOUNT_OVERFLOW,
  TAX_RATE_UNAVAILABLE,
  MISSING_TAX_AUTHORITY,
  MISSING_FEE_CONFIGURATION,
  PSP_UNAVAILABLE,
  /** The PSP did not create the order; the same request may be sent again. */
  PSP_RETRYABLE
}
