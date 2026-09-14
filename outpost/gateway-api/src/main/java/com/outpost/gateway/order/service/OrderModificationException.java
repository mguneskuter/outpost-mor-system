package com.outpost.gateway.order.service;

/** A request to modify an order that is refused. */
public final class OrderModificationException extends RuntimeException {
  private final OrderModificationErrorCodes code;

  /** Creates the refusal for {@code code}. */
  public OrderModificationException(OrderModificationErrorCodes code) {
    super(code.name());
    this.code = code;
  }

  /** Returns why the request was refused. */
  public OrderModificationErrorCodes code() {
    return code;
  }
}
