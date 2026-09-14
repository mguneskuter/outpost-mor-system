package com.outpost.gateway.order.service;

/** A request to create an order that is refused. */
public final class CreateOrderException extends RuntimeException {
  private final CreateOrderErrorCodes code;

  /** Creates the refusal for {@code code}. */
  public CreateOrderException(CreateOrderErrorCodes code) {
    super(code.name());
    this.code = code;
  }

  /** Returns why the request was refused. */
  public CreateOrderErrorCodes code() {
    return code;
  }
}
