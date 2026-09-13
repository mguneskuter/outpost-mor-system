package com.outpost.account.configuration.repository.mybatis;

import com.outpost.account.Account;
import com.outpost.account.configuration.repository.MerchantPspRepository;
import com.outpost.account.repository.AccountRepository;
import java.util.List;

/** Reads {@code merchant_psp}: a PSP is enabled for a merchant when their row exists. */
public final class MyBatisMerchantPspRepository implements MerchantPspRepository {
  private final MerchantPspMapper mapper;
  private final AccountRepository accounts;

  /** Creates a repository over its mapper and the account repository that loads each PSP. */
  public MyBatisMerchantPspRepository(MerchantPspMapper mapper, AccountRepository accounts) {
    this.mapper = mapper;
    this.accounts = accounts;
  }

  @Override
  public boolean isPspEnabled(long merchantAccountId, long pspAccountId) {
    return mapper.isPspEnabled(merchantAccountId, pspAccountId);
  }

  @Override
  public List<Account> findEnabledPsps(long merchantAccountId) {
    return mapper.findEnabledPspAccountIds(merchantAccountId).stream()
        .map(
            pspAccountId ->
                accounts
                    .findAccountById(pspAccountId)
                    .orElseThrow(
                        () -> new IllegalStateException("enabled PSP account is not stored")))
        .toList();
  }
}
