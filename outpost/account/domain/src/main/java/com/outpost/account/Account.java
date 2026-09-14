package com.outpost.account;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * An account (a party) in the account hierarchy: a merchant, bank account, PSP, tax authority, or
 * the platform. Every non-root account is created with an existing parent.
 */
public final class Account {

  private final long accountId;
  private final AccountTypes.AccountType accountType;
  private final String code;
  private final String name;
  private final boolean active;
  private final Instant createdAt;
  @Nullable private Account parentAccount;
  private final List<Account> childAccounts = new ArrayList<>();

  private Account(
      long accountId,
      AccountTypes.AccountType accountType,
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
      parentAccount.attachChild(this);
    }
  }

  /**
   * Creates an account. A root account has no parent; every other account must be created under an
   * existing parent that permits its type.
   */
  public static Account of(
      long accountId,
      AccountTypes.AccountType accountType,
      String code,
      String name,
      boolean active,
      Instant createdAt,
      @Nullable Account parentAccount) {
    if (accountType == null) {
      throw new IllegalArgumentException("accountType must not be null");
    }
    if (accountType.equals(AccountTypes.ROOT.getValue())) {
      if (parentAccount != null) {
        throw new IllegalArgumentException("the ROOT account must not have a parent");
      }
    } else {
      if (parentAccount == null) {
        throw new IllegalArgumentException("a non-root account must have a parent");
      }
      if (!parentAccount.getAccountType().isAllowedChildType(accountType)) {
        throw new IllegalArgumentException(
            "account type "
                + parentAccount.getAccountType().getCode()
                + " cannot have a child of type "
                + accountType.getCode());
      }
    }
    return new Account(accountId, accountType, code, name, active, createdAt, parentAccount);
  }

  /** Returns the immutable id of the account. */
  public long getAccountId() {
    return accountId;
  }

  /** Returns the account's immutable type. */
  public AccountTypes.AccountType getAccountType() {
    return accountType;
  }

  /** Returns the account's code. */
  public String getCode() {
    return code;
  }

  /** Returns the account's display name. */
  public String getName() {
    return name;
  }

  /** Returns whether the account is active. */
  public boolean isActive() {
    return active;
  }

  /** Returns the creation timestamp. */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /**
   * Returns the parent account. Empty only for the root.
   *
   * @throws IllegalStateException when a non-root account has no parent.
   */
  public Optional<Account> getParentAccount() {
    if (parentAccount == null && !accountType.equals(AccountTypes.ROOT.getValue())) {
      throw new IllegalStateException("a non-root account must have a parent");
    }
    return Optional.ofNullable(parentAccount);
  }

  /** Returns an unmodifiable view of the child accounts; may be empty. */
  public List<Account> getChildAccounts() {
    return Collections.unmodifiableList(childAccounts);
  }

  void attachChild(Account childAccount) {
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
    return accountId == that.accountId
        && Objects.equals(accountType, that.accountType)
        && Objects.equals(code, that.code)
        && Objects.equals(name, that.name)
        && Objects.equals(parentAccount, that.parentAccount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(accountId, accountType, code, name, parentAccount);
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
}
