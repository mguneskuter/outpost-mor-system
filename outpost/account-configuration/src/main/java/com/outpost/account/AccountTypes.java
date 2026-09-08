package com.outpost.account;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The set of account types an account can carry in this platform (for example MERCHANT for a
 * merchant, BANK_ACCOUNT for a payout bank child). Each constant owns the single {@link
 * AccountType} value that carries its stable surrogate identifier and code.
 */
public enum AccountTypes {
  ROOT(1L, "ROOT"),
  MERCHANT(2L, "MERCHANT"),
  BANK_ACCOUNT(3L, "BANK_ACCOUNT"),
  PSP(4L, "PSP"),
  TAX_AUTHORITY(5L, "TAX_AUTHORITY"),
  PLATFORM(6L, "PLATFORM");

  private static final Map<String, AccountTypes> BY_CODE =
      Arrays.stream(values())
          .collect(
              Collectors.toUnmodifiableMap(
                  constant -> constant.value().code(), constant -> constant));

  @SuppressWarnings("Immutable")
  private final AccountType value;

  AccountTypes(long accountTypeId, String code) {
    value = new AccountType(accountTypeId, code);
  }

  /** Returns the account type value owned by this constant. */
  public AccountType value() {
    return value;
  }

  /** Returns the account type for an exact, case-sensitive code, if any. */
  public static Optional<AccountType> fromCode(String code) {
    return Optional.ofNullable(BY_CODE.get(Objects.requireNonNull(code, "code")))
        .map(AccountTypes::value);
  }

  /** Immutable account-type value owned by one {@link AccountTypes} constant. */
  public static final class AccountType {

    private static final Pattern CODE = Pattern.compile("[A-Z]+(_[A-Z]+)*");

    private final long accountTypeId;
    private final String code;

    private AccountType(long accountTypeId, String code) {
      if (accountTypeId <= 0) {
        throw new IllegalArgumentException("accountTypeId must be positive: " + accountTypeId);
      }
      if (code == null || code.isBlank() || !CODE.matcher(code).matches()) {
        throw new IllegalArgumentException(
            "code must be uppercase ASCII letters separated by underscores: " + code);
      }
      this.accountTypeId = accountTypeId;
      this.code = code;
    }

    /** Returns the stable account-type identifier. */
    public long accountTypeId() {
      return accountTypeId;
    }

    /** Returns the exact account-type code. */
    public String code() {
      return code;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof AccountType that)) {
        return false;
      }
      return accountTypeId == that.accountTypeId && code.equals(that.code);
    }

    @Override
    public int hashCode() {
      return Objects.hash(accountTypeId, code);
    }

    @Override
    public String toString() {
      return "AccountType{accountTypeId=" + accountTypeId + ", code=" + code + '}';
    }
  }
}
