package com.outpost.gateway.order.service;

/** Controlled failure returned by the order modification boundary. */
public final class OrderModificationException extends RuntimeException {
  private final int status;
  private final String code;

  /** Creates a controlled order modification failure. */
  public OrderModificationException(int status, String code) {
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
