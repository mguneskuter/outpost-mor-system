package com.outpost.payment;

import com.outpost.platform.staticdata.StaticData;

/** Outcomes for processed PSP events. */
@StaticData
public enum PspEventResults {
  SUCCESS(1L),
  FAILED(2L);

  private final PspEventResult value;

  PspEventResults(long id) {
    value = new PspEventResult(id, name());
  }

  public PspEventResult getValue() {
    return value;
  }

  /** Persisted PSP event result value. */
  public record PspEventResult(long pspEventResultId, String code) {
    public long getPspEventResultId() {
      return pspEventResultId;
    }

    public String getCode() {
      return code;
    }
  }
}
