package com.outpost.accounting;

import com.outpost.platform.staticdata.StaticData;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** The event types recorded against a transaction. */
@StaticData
public enum TransactionEventTypes {
  ORDER_CREATED(1L, "ORDER_CREATED", false),
  AUTHORISED(2L, "AUTHORISED", false),
  REFUSED(3L, "REFUSED", false),
  CANCELLED(4L, "CANCELLED", false),
  CAPTURED(5L, "CAPTURED", true),
  CAPTURE_FAILED(6L, "CAPTURE_FAILED", false),
  REFUND_REQUESTED(7L, "REFUND_REQUESTED", false),
  REFUND_ACCEPTED(8L, "REFUND_ACCEPTED", false),
  REFUNDED(9L, "REFUNDED", true),
  REFUND_FAILED(10L, "REFUND_FAILED", false);

  private static final Map<String, TransactionEventTypes> BY_CODE =
      Arrays.stream(values())
          .collect(Collectors.toUnmodifiableMap(type -> type.value.code, type -> type));

  @SuppressWarnings("Immutable")
  private final TransactionEventType value;

  TransactionEventTypes(long transactionEventTypeId, String code, boolean requiresJournalEntry) {
    value = new TransactionEventType(transactionEventTypeId, code, requiresJournalEntry);
  }

  /** Returns this constant's value. */
  public TransactionEventType getValue() {
    return value;
  }

  /** Looks up an exact code. */
  public static Optional<TransactionEventType> fromCode(String code) {
    return Optional.ofNullable(BY_CODE.get(Objects.requireNonNull(code, "code")))
        .map(TransactionEventTypes::getValue);
  }

  /** Immutable transaction-event-type value owned by one {@link TransactionEventTypes} constant. */
  public static final class TransactionEventType {
    private static final Pattern CODE = Pattern.compile("[A-Z]+(_[A-Z]+)*");
    private final long transactionEventTypeId;
    private final String code;
    private final boolean requiresJournalEntry;

    private TransactionEventType(
        long transactionEventTypeId, String code, boolean requiresJournalEntry) {
      if (transactionEventTypeId <= 0) {
        throw new IllegalArgumentException(
            "transactionEventTypeId must be positive: " + transactionEventTypeId);
      }
      if (code == null || code.isBlank() || !CODE.matcher(code).matches()) {
        throw new IllegalArgumentException("code must be uppercase ASCII words: " + code);
      }
      this.transactionEventTypeId = transactionEventTypeId;
      this.code = code;
      this.requiresJournalEntry = requiresJournalEntry;
    }

    /** Returns the permanent identifier. */
    public long getTransactionEventTypeId() {
      return transactionEventTypeId;
    }

    /** Returns the exact code. */
    public String getCode() {
      return code;
    }

    /** Returns whether this event requires a journal entry. */
    public boolean isRequiresJournalEntry() {
      return requiresJournalEntry;
    }
  }
}
