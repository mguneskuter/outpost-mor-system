package com.outpost.accounting;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** The entry types supported by accounting. */
public enum JournalEntryTypes {
  CAPTURE(1L, "CAPTURE"),
  REFUND(2L, "REFUND");

  private static final Map<String, JournalEntryTypes> BY_CODE =
      Arrays.stream(values())
          .collect(Collectors.toUnmodifiableMap(type -> type.value.code, type -> type));

  @SuppressWarnings("Immutable")
  private final JournalEntryType value;

  JournalEntryTypes(long journalEntryTypeId, String code) {
    value = new JournalEntryType(journalEntryTypeId, code);
  }

  /** Returns this constant's value. */
  public JournalEntryType value() {
    return value;
  }

  /** Looks up an exact code. */
  public static Optional<JournalEntryType> fromCode(String code) {
    return Optional.ofNullable(BY_CODE.get(Objects.requireNonNull(code, "code")))
        .map(JournalEntryTypes::value);
  }

  /** Immutable catalogue value owned exclusively by a {@link JournalEntryTypes} constant. */
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
    public long journalEntryTypeId() {
      return journalEntryTypeId;
    }

    /** Returns the exact code. */
    public String code() {
      return code;
    }
  }
}
