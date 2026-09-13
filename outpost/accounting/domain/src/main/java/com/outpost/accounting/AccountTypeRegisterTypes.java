package com.outpost.accounting;

import com.outpost.account.AccountTypes;
import com.outpost.account.AccountTypes.AccountType;
import com.outpost.accounting.RegisterTypes.RegisterType;
import com.outpost.platform.staticdata.StaticData;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * A permitted account-type and register-type pair. An account receives every register type mapped
 * to its type exactly once, and several register types per account type are valid. Each pair
 * carries the register's normal balance.
 */
@StaticData
public enum AccountTypeRegisterTypes {
  MERCHANT_MERCHANT_PAYABLE(
      1L, AccountTypes.MERCHANT, RegisterTypes.MERCHANT_PAYABLE, NormalBalances.CREDIT),
  BANK_ACCOUNT_PAYOUT_PAYABLE(
      2L, AccountTypes.BANK_ACCOUNT, RegisterTypes.PAYOUT_PAYABLE, NormalBalances.CREDIT),
  PSP_PSP_RECEIVABLE(3L, AccountTypes.PSP, RegisterTypes.PSP_RECEIVABLE, NormalBalances.DEBIT),
  TAX_AUTHORITY_TAX_PAYABLE(
      4L, AccountTypes.TAX_AUTHORITY, RegisterTypes.TAX_PAYABLE, NormalBalances.CREDIT),
  PLATFORM_FEE_REVENUE(5L, AccountTypes.PLATFORM, RegisterTypes.FEE_REVENUE, NormalBalances.CREDIT),
  PLATFORM_FX_CLEARING(6L, AccountTypes.PLATFORM, RegisterTypes.FX_CLEARING, NormalBalances.DEBIT),
  PLATFORM_FX_FEE_REVENUE(
      7L, AccountTypes.PLATFORM, RegisterTypes.FX_FEE_REVENUE, NormalBalances.CREDIT),
  MERCHANT_PENDING_FEE(8L, AccountTypes.MERCHANT, RegisterTypes.PENDING_FEE, NormalBalances.DEBIT),
  PLATFORM_PENDING_FEE(9L, AccountTypes.PLATFORM, RegisterTypes.PENDING_FEE, NormalBalances.CREDIT);

  @SuppressWarnings("Immutable")
  private final AccountTypeRegisterType value;

  private final NormalBalances normalBalance;

  AccountTypeRegisterTypes(
      long id, AccountTypes accountType, RegisterTypes registerType, NormalBalances normalBalance) {
    value = new AccountTypeRegisterType(id, accountType.getValue(), registerType.getValue());
    this.normalBalance = normalBalance;
  }

  /** Returns this constant's permitted pair. */
  public AccountTypeRegisterType getValue() {
    return value;
  }

  /** Returns the side on which this pair's register balance is positive. */
  public NormalBalances normalBalance() {
    return normalBalance;
  }

  /** Looks up the constant permitting this account type and register type together. */
  public static Optional<AccountTypeRegisterTypes> fromAccountTypeAndRegisterType(
      AccountType accountType, RegisterType registerType) {
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(registerType, "registerType");
    return Arrays.stream(values())
        .filter(
            constant ->
                constant.value.accountType.equals(accountType)
                    && constant.value.registerType.equals(registerType))
        .findFirst();
  }

  /** Immutable permitted account-type and register-type pair. */
  public static final class AccountTypeRegisterType {
    private final long accountTypeRegisterTypeId;
    private final AccountType accountType;
    private final RegisterTypes.RegisterType registerType;

    private AccountTypeRegisterType(
        long accountTypeRegisterTypeId,
        AccountType accountType,
        RegisterTypes.RegisterType registerType) {
      if (accountTypeRegisterTypeId <= 0) {
        throw new IllegalArgumentException(
            "accountTypeRegisterTypeId must be positive: " + accountTypeRegisterTypeId);
      }
      this.accountTypeRegisterTypeId = accountTypeRegisterTypeId;
      this.accountType = Objects.requireNonNull(accountType, "accountType");
      this.registerType = Objects.requireNonNull(registerType, "registerType");
    }

    public long getAccountTypeRegisterTypeId() {
      return accountTypeRegisterTypeId;
    }

    public AccountType getAccountType() {
      return accountType;
    }

    public RegisterTypes.RegisterType getRegisterType() {
      return registerType;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof AccountTypeRegisterType that)) {
        return false;
      }
      return accountTypeRegisterTypeId == that.accountTypeRegisterTypeId
          && accountType.equals(that.accountType)
          && registerType.equals(that.registerType);
    }

    @Override
    public int hashCode() {
      return Objects.hash(accountTypeRegisterTypeId, accountType, registerType);
    }
  }
}
