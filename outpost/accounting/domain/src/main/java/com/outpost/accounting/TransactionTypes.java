package com.outpost.accounting;

import com.outpost.platform.staticdata.StaticData;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** The transaction families supported by accounting. */
@StaticData
public enum TransactionTypes {
  PAYMENT(1L, "PAYMENT"),
  CAPTURE(2L, "CAPTURE"),
  REFUND(3L, "REFUND");

  private static final Map<String, TransactionTypes> BY_CODE =
      Arrays.stream(values())
          .collect(Collectors.toUnmodifiableMap(type -> type.value.code, type -> type));

  @SuppressWarnings("Immutable")
  private final TransactionType value;

  TransactionTypes(long transactionTypeId, String code) {
    value = new TransactionType(transactionTypeId, code);
  }

  /** Returns this constant's value. */
  public TransactionType getValue() {
    return value;
  }

  /** Looks up an exact code. */
  public static Optional<TransactionType> fromCode(String code) {
    return Optional.ofNullable(BY_CODE.get(Objects.requireNonNull(code, "code")))
        .map(TransactionTypes::getValue);
  }

  /** Immutable catalogue value owned exclusively by a {@link TransactionTypes} constant. */
  public static final class TransactionType {
    private static final Pattern CODE = Pattern.compile("[A-Z]+(_[A-Z]+)*");
    private final long transactionTypeId;
    private final String code;

    private TransactionType(long transactionTypeId, String code) {
      if (transactionTypeId <= 0) {
        throw new IllegalArgumentException(
            "transactionTypeId must be positive: " + transactionTypeId);
      }
      if (code == null || code.isBlank() || !CODE.matcher(code).matches()) {
        throw new IllegalArgumentException("code must be uppercase ASCII words: " + code);
      }
      this.transactionTypeId = transactionTypeId;
      this.code = code;
    }

    /** Returns the permanent identifier. */
    public long getTransactionTypeId() {
      return transactionTypeId;
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
      if (!(other instanceof TransactionType that)) {
        return false;
      }
      return transactionTypeId == that.transactionTypeId && code.equals(that.code);
    }

    @Override
    public int hashCode() {
      return Objects.hash(transactionTypeId, code);
    }
  }
}
