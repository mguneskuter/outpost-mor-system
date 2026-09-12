package com.outpost.gateway.order.service;

/** Durable progress markers for an order creation operation. */
public enum OrderPhases {
  ORDER_PERSISTED,
  LEDGER_CREATED,
  PSP_CREATED,
  COMPLETED;

  /** Returns the database representation. */
  public String code() {
    return name();
  }
}
