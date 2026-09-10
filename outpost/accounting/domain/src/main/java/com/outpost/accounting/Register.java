package com.outpost.accounting;

import com.outpost.account.Account;
import com.outpost.accounting.RegisterTypes.RegisterType;
import java.util.Objects;

/** A register is the pairing of one account with one accounting purpose. */
public final class Register {
  private final long registerId;
  private final Account account;
  private final RegisterType registerType;

  /** Creates a register. */
  public Register(long registerId, Account account, RegisterType registerType) {
    if (registerId <= 0) {
      throw new IllegalArgumentException("registerId must be positive: " + registerId);
    }
    this.registerId = registerId;
    this.account = Objects.requireNonNull(account, "account");
    this.registerType = Objects.requireNonNull(registerType, "registerType");
  }

  /** Returns the register identity. */
  public long getRegisterId() {
    return registerId;
  }

  /** Returns the owning account. */
  public Account getAccount() {
    return account;
  }

  /** Returns the accounting purpose. */
  public RegisterType getRegisterType() {
    return registerType;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Register that)) {
      return false;
    }
    return registerId == that.registerId
        && Objects.equals(account, that.account)
        && Objects.equals(registerType, that.registerType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(registerId, account, registerType);
  }
}
