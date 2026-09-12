package com.outpost.payment;

import com.outpost.platform.staticdata.StaticData;

/** Processing statuses for PSP events. */
@StaticData
public enum PspEventStatuses {
  RECEIVED(1L),
  IN_PROGRESS(2L),
  DONE(3L);

  private final PspEventStatus value;

  PspEventStatuses(long id) {
    value = new PspEventStatus(id, name());
  }

  public PspEventStatus getValue() {
    return value;
  }

  /** Persisted PSP event status value. */
  public record PspEventStatus(long pspEventStatusId, String code) {
    public long getPspEventStatusId() {
      return pspEventStatusId;
    }

    public String getCode() {
      return code;
    }
  }
}
