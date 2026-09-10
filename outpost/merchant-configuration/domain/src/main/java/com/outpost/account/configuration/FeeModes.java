package com.outpost.account.configuration;

import com.outpost.platform.staticdata.StaticData;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** The fee modes supported by Outpost, each owning exactly one value. */
@StaticData
public enum FeeModes {
  PERCENTAGE(1L, "PERCENTAGE"),
  PERCENTAGE_PLUS_FIXED(2L, "PERCENTAGE_PLUS_FIXED");

  private static final Map<String, FeeModes> BY_CODE =
      Arrays.stream(values())
          .collect(
              Collectors.toUnmodifiableMap(
                  feeMode -> feeMode.getValue().getCode(), feeMode -> feeMode));

  @SuppressWarnings("Immutable")
  private final FeeMode value;

  FeeModes(long feeModeId, String code) {
    value = new FeeMode(feeModeId, code);
  }

  /** Returns the fee mode owned by this constant. */
  public FeeMode getValue() {
    return value;
  }

  /** Returns the fee mode for an exact, case-sensitive code, if any. */
  public static Optional<FeeMode> fromCode(String code) {
    return Optional.ofNullable(BY_CODE.get(Objects.requireNonNull(code, "code")))
        .map(FeeModes::getValue);
  }

  /** Immutable fee mode value owned by one {@link FeeModes} constant. */
  public static final class FeeMode {
    private final long feeModeId;
    private final String code;

    private FeeMode(long feeModeId, String code) {
      if (feeModeId <= 0) {
        throw new IllegalArgumentException("feeModeId must be positive: " + feeModeId);
      }
      if (code == null || code.isBlank()) {
        throw new IllegalArgumentException("code must not be null or blank");
      }
      this.feeModeId = feeModeId;
      this.code = code;
    }

    /** Returns the stable fee-mode identifier. */
    public long getFeeModeId() {
      return feeModeId;
    }

    /** Returns the exact fee-mode code. */
    public String getCode() {
      return code;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof FeeMode that)) {
        return false;
      }
      return feeModeId == that.feeModeId && code.equals(that.code);
    }

    @Override
    public int hashCode() {
      return Objects.hash(feeModeId, code);
    }

    @Override
    public String toString() {
      return "FeeMode{feeModeId=" + feeModeId + ", code=" + code + '}';
    }
  }
}
