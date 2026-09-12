package com.outpost.payment;

import com.outpost.platform.staticdata.StaticData;

/** PSP event types accepted by the payment domain. */
@StaticData
public enum PspEventCodes {
  AUTHORISATION(1L),
  CAPTURE(2L),
  REFUND(3L),
  CANCELLATION(4L);

  private final PspEventCode value;

  PspEventCodes(long id) {
    value = new PspEventCode(id, name());
  }

  public PspEventCode getValue() {
    return value;
  }

  /** Persisted PSP event code value. */
  public record PspEventCode(long pspEventCodeId, String code) {
    public long getPspEventCodeId() {
      return pspEventCodeId;
    }

    public String getCode() {
      return code;
    }
  }
}
