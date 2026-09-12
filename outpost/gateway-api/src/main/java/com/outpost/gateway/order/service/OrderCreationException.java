package com.outpost.gateway.order.service;

/** Controlled failure returned by the order creation boundary. */
public final class OrderCreationException extends RuntimeException {
  private final int status;
  private final String code;

  /** Creates a controlled order failure. */
  public OrderCreationException(int status, String code) {
    super(code);
    this.status = status;
    this.code = code;
  }

  /** Returns the HTTP status. */
  public int status() {
    return status;
  }

  /** Returns the stable error code. */
  public String code() {
    return code;
  }
}
