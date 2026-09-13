package com.outpost.account.repository;

import com.outpost.account.Account;
import java.util.Optional;

/** Reads accounts, each with its chain of parent accounts. */
public interface AccountRepository {
  /** Finds the account with this id. */
  Optional<Account> findAccountById(long accountId);

  /** Finds the account with this code. */
  Optional<Account> findAccountByCode(String code);
}
