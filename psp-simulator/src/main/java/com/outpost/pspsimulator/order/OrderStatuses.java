package com.outpost.pspsimulator.order;

/** The states an order moves through on its PSP before capture, refusal, or cancellation. */
public enum OrderStatuses {
  CREATED,
  AUTHORISED,
  REFUSED,
  CAPTURED,
  CANCELLED;

  /** Returns the exact status code, which is also the persisted value. */
  public String getCode() {
    return name();
  }

  /** Returns the status for an exact, case-sensitive code. */
  public static OrderStatuses fromCode(String code) {
    for (OrderStatuses status : values()) {
      if (status.getCode().equals(code)) {
        return status;
      }
    }
    throw new IllegalArgumentException("unknown order status code: " + code);
  }
}
