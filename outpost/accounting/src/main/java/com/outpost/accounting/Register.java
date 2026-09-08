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
  public long registerId() {
    return registerId;
  }

  /** Returns the owning account. */
  public Account account() {
    return account;
  }

  /** Returns the accounting purpose. */
  public RegisterType registerType() {
    return registerType;
  }

  @Override
  public boolean equals(Object other) {
    return this == other || (other instanceof Register that && registerId == that.registerId);
  }

  @Override
  public int hashCode() {
    return Long.hashCode(registerId);
  }
}
