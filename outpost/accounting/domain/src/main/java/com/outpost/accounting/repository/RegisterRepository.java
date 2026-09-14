package com.outpost.accounting.repository;

import com.outpost.account.Account;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes.RegisterType;
import java.util.Optional;

/** Reads registers. */
public interface RegisterRepository {
  /** Finds the register of {@code registerType} that {@code account} holds. */
  Optional<Register> findRegisterByAccountAndRegisterType(
      Account account, RegisterType registerType);
}
