package com.outpost.account;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
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
    if (code == null) {
      throw new NullPointerException("code must not be null");
    }
    return Optional.ofNullable(BY_CODE.get(code)).map(AccountTypes::value);
  }
}
