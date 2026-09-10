package com.outpost.accounting;

import com.outpost.platform.staticdata.StaticData;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** The register types owned by the accounting bounded context. */
@StaticData
public enum RegisterTypes {
  MERCHANT_PAYABLE(1L, "MERCHANT_PAYABLE"),
  PAYOUT_PAYABLE(2L, "PAYOUT_PAYABLE"),
  PSP_RECEIVABLE(3L, "PSP_RECEIVABLE"),
  TAX_PAYABLE(4L, "TAX_PAYABLE"),
  FEE_REVENUE(5L, "FEE_REVENUE"),
  FX_CLEARING(6L, "FX_CLEARING"),
  FX_FEE_REVENUE(7L, "FX_FEE_REVENUE");

  private static final Map<String, RegisterTypes> BY_CODE =
      Arrays.stream(values())
          .collect(Collectors.toUnmodifiableMap(type -> type.value.code, type -> type));

  @SuppressWarnings("Immutable")
  private final RegisterType value;

  RegisterTypes(long registerTypeId, String code) {
    value = new RegisterType(registerTypeId, code);
  }

  /** Returns this constant's value. */
  public RegisterType getValue() {
    return value;
  }

  /** Looks up an exact code. */
  public static Optional<RegisterType> fromCode(String code) {
    return Optional.ofNullable(BY_CODE.get(Objects.requireNonNull(code, "code")))
        .map(RegisterTypes::getValue);
  }

  /** Immutable catalogue value owned exclusively by a {@link RegisterTypes} constant. */
  public static final class RegisterType {
    private static final Pattern CODE = Pattern.compile("[A-Z]+(_[A-Z]+)*");
    private final long registerTypeId;
    private final String code;

    private RegisterType(long registerTypeId, String code) {
      if (registerTypeId <= 0) {
        throw new IllegalArgumentException("registerTypeId must be positive: " + registerTypeId);
      }
      if (code == null || code.isBlank() || !CODE.matcher(code).matches()) {
        throw new IllegalArgumentException("code must be uppercase ASCII words: " + code);
      }
      this.registerTypeId = registerTypeId;
      this.code = code;
    }

    /** Returns the permanent identifier. */
    public long getRegisterTypeId() {
      return registerTypeId;
    }

    /** Returns the exact code. */
    public String getCode() {
      return code;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof RegisterType that)) {
        return false;
      }
      return registerTypeId == that.registerTypeId && code.equals(that.code);
    }

    @Override
    public int hashCode() {
      return Objects.hash(registerTypeId, code);
    }
  }
}
