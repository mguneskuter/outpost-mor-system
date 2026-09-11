package com.outpost.accounting;

import com.outpost.platform.staticdata.StaticData;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** The entry types supported by accounting. */
@StaticData
public enum JournalEntryTypes {
  CAPTURE(1L, "CAPTURE"),
  REFUND(2L, "REFUND"),
  FEE_PENDING(3L, "FEE_PENDING"),
  FEE_RELEASE(4L, "FEE_RELEASE");

  private static final Map<String, JournalEntryTypes> BY_CODE =
      Arrays.stream(values())
          .collect(Collectors.toUnmodifiableMap(type -> type.value.code, type -> type));

  @SuppressWarnings("Immutable")
  private final JournalEntryType value;

  JournalEntryTypes(long journalEntryTypeId, String code) {
    value = new JournalEntryType(journalEntryTypeId, code);
  }

  /** Returns this constant's value. */
  public JournalEntryType getValue() {
    return value;
  }

  /** Looks up an exact code. */
  public static Optional<JournalEntryType> fromCode(String code) {
    return Optional.ofNullable(BY_CODE.get(Objects.requireNonNull(code, "code")))
        .map(JournalEntryTypes::getValue);
  }

  /** Immutable journal-entry-type value owned by one {@link JournalEntryTypes} constant. */
  public static final class JournalEntryType {
    private static final Pattern CODE = Pattern.compile("[A-Z]+(_[A-Z]+)*");
    private final long journalEntryTypeId;
    private final String code;

    private JournalEntryType(long journalEntryTypeId, String code) {
      if (journalEntryTypeId <= 0) {
        throw new IllegalArgumentException(
            "journalEntryTypeId must be positive: " + journalEntryTypeId);
      }
      if (code == null || code.isBlank() || !CODE.matcher(code).matches()) {
        throw new IllegalArgumentException("code must be uppercase ASCII words: " + code);
      }
      this.journalEntryTypeId = journalEntryTypeId;
      this.code = code;
    }

    /** Returns the permanent identifier. */
    public long getJournalEntryTypeId() {
      return journalEntryTypeId;
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
      if (!(other instanceof JournalEntryType that)) {
        return false;
      }
      return journalEntryTypeId == that.journalEntryTypeId && code.equals(that.code);
    }

    @Override
    public int hashCode() {
      return Objects.hash(journalEntryTypeId, code);
    }
  }
}
