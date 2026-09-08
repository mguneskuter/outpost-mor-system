package com.outpost.account;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * An account (a party) in the account hierarchy: a merchant, bank account, PSP, tax authority, or
 * the platform. Every non-root account is created with an existing parent, so a complete account is
 * never observed in a partially assembled state.
 */
public final class Account {

  private static final Map<AccountType, Set<AccountType>> ALLOWED_CHILDREN_TYPES =
      buildAllowedChildrenTypes();

  private final long accountId;
  private final AccountType accountType;
  private final String code;
  private final String name;
  private final boolean active;
  private final Instant createdAt;
  @Nullable private Account parentAccount;
  private final List<Account> childAccounts = new ArrayList<>();

  private Account(
      long accountId,
      AccountType accountType,
      String code,
      String name,
      boolean active,
      Instant createdAt,
      @Nullable Account parentAccount) {
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive: " + accountId);
    }
    this.accountId = accountId;
    if (accountType == null) {
      throw new IllegalArgumentException("accountType must not be null");
    }
    this.accountType = accountType;
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("code must not be null or blank");
    }
    this.code = code;
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be null or blank");
    }
    this.name = name;
    this.active = active;
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt must not be null");
    }
    this.createdAt = createdAt;
    this.parentAccount = parentAccount;
    if (parentAccount != null) {
      parentAccount.addChild(this);
    }
  }

  /**
   * Creates an account. A root account has no parent; every other account must be created under an
   * existing parent that permits its type.
   */
  public static Account of(
      long accountId,
      AccountType accountType,
      String code,
      String name,
      boolean active,
      Instant createdAt,
      @Nullable Account parentAccount) {
    if (accountType == null) {
      throw new IllegalArgumentException("accountType must not be null");
    }
    if (accountType.equals(AccountTypes.ROOT.value())) {
      if (parentAccount != null) {
        throw new IllegalArgumentException("the ROOT account must not have a parent");
      }
    } else {
      if (parentAccount == null) {
        throw new IllegalArgumentException("a non-root account must have a parent");
      }
      if (!parentAccount.allowedChildAccountType(accountType)) {
        throw new IllegalArgumentException(
            "account type "
                + parentAccount.accountType().code()
                + " cannot have a child of type "
                + accountType.code());
      }
    }
    return new Account(accountId, accountType, code, name, active, createdAt, parentAccount);
  }

  /** Returns the immutable positive account id. */
  public long accountId() {
    return accountId;
  }

  /** Returns the account's immutable type. */
  public AccountType accountType() {
    return accountType;
  }

  /** Returns the exact, unnormalised code. */
  public String code() {
    return code;
  }

  /** Returns the exact, unnormalised name. */
  public String name() {
    return name;
  }

  /** Returns whether the account is active. */
  public boolean active() {
    return active;
  }

  /** Returns the creation instant. */
  public Instant createdAt() {
    return createdAt;
  }

  /**
   * Returns the parent account. Empty only for the root.
   *
   * @throws IllegalStateException when a non-root account has no parent.
   */
  public Optional<Account> parentAccount() {
    if (parentAccount == null && !accountType.equals(AccountTypes.ROOT.value())) {
      throw new IllegalStateException("a non-root account must have a parent");
    }
    return Optional.ofNullable(parentAccount);
  }

  /** Returns an unmodifiable view of the child accounts; may be empty. */
  public List<Account> childAccounts() {
    return Collections.unmodifiableList(childAccounts);
  }

  private boolean allowedChildAccountType(AccountType childType) {
    return isAllowedChildrenType(accountType, childType);
  }

  void addChild(Account childAccount) {
    childAccounts.add(childAccount);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Account that)) {
      return false;
    }
    return accountId == that.accountId;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(accountId);
  }

  @Override
  public String toString() {
    return "Account{accountId="
        + accountId
        + ", accountType="
        + accountType
        + ", code="
        + code
        + ", name="
        + name
        + ", active="
        + active
        + ", createdAt="
        + createdAt
        + '}';
  }

  private static boolean isAllowedChildrenType(AccountType parentType, AccountType childType) {
    return ALLOWED_CHILDREN_TYPES.getOrDefault(parentType, Set.of()).contains(childType);
  }

  private static Map<AccountType, Set<AccountType>> buildAllowedChildrenTypes() {
    return Map.of(
        AccountTypes.ROOT.value(),
            Set.of(
                AccountTypes.MERCHANT.value(),
                AccountTypes.PSP.value(),
                AccountTypes.PLATFORM.value(),
                AccountTypes.TAX_AUTHORITY.value()),
        AccountTypes.MERCHANT.value(), Set.of(AccountTypes.BANK_ACCOUNT.value()),
        AccountTypes.PSP.value(), Set.of(AccountTypes.BANK_ACCOUNT.value()),
        AccountTypes.PLATFORM.value(), Set.of(AccountTypes.BANK_ACCOUNT.value()),
        AccountTypes.TAX_AUTHORITY.value(), Set.of(AccountTypes.BANK_ACCOUNT.value()),
        AccountTypes.BANK_ACCOUNT.value(), Set.of());
  }
}
