package com.outpost.payment.repository.mybatis;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.account.AccountTypes.AccountType;
import com.outpost.account.repository.AccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** The accounts a test stored, found by id or code without reading the account tables. */
final class KnownAccounts implements AccountRepository {
  private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
  private static final Account ROOT =
      Account.of(1L, AccountTypes.ROOT.getValue(), "ROOT", "Root", true, CREATED, null);

  private final List<Account> accounts;

  KnownAccounts(Account... accounts) {
    this.accounts = List.of(accounts);
  }

  /** An account of {@code type} under a root account, for an account row the test inserted. */
  static Account underRoot(long accountId, AccountTypes type, String code) {
    return Account.of(accountId, type.getValue(), code, code, true, CREATED, ROOT);
  }

  @Override
  public Optional<Account> findAccountById(long accountId) {
    return accounts.stream().filter(account -> account.getAccountId() == accountId).findFirst();
  }

  @Override
  public Optional<Account> findAccountByCode(String code) {
    return accounts.stream().filter(account -> account.getCode().equals(code)).findFirst();
  }

  @Override
  public Optional<Account> findTaxAuthorityAccountByCountryId(long countryId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<Account> findAccountByAccountType(AccountType accountType) {
    throw new UnsupportedOperationException();
  }
}
