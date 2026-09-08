package com.outpost.account;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A fixed account type from the platform's account-type set, carried by an {@link Account} to say
 * what kind of party it is in the account hierarchy. A value is owned by exactly one {@link
 * AccountTypes} constant and cannot be constructed elsewhere.
 */
public final class AccountType {

  private static final Pattern CODE = Pattern.compile("[A-Z]+(_[A-Z]+)*");

  private final long accountTypeId;
  private final String code;

  AccountType(long accountTypeId, String code) {
    if (accountTypeId <= 0) {
      throw new IllegalArgumentException("accountTypeId must be positive: " + accountTypeId);
    }
    this.accountTypeId = accountTypeId;
    if (code == null) {
      throw new IllegalArgumentException("code must not be null");
    }
    if (code.isBlank() || !CODE.matcher(code).matches()) {
      throw new IllegalArgumentException(
          "code must be uppercase ASCII letters separated by underscores: " + code);
    }
    this.code = code;
  }

  /** Returns the immutable surrogate identifier. */
  public long accountTypeId() {
    return accountTypeId;
  }

  /** Returns the exact, case-sensitive code. */
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
