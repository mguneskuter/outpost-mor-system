package com.outpost.account.configuration.repository.mybatis;

import com.outpost.account.configuration.repository.MerchantPspRepository;

/** Reads {@code merchant_psp}: a PSP is enabled for a merchant when their row exists. */
public final class MyBatisMerchantPspRepository implements MerchantPspRepository {
  private final MerchantPspMapper mapper;

  /** Creates a repository over its mapper. */
  public MyBatisMerchantPspRepository(MerchantPspMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public boolean isPspEnabled(long merchantAccountId, long pspAccountId) {
    return mapper.isPspEnabled(merchantAccountId, pspAccountId);
  }
}
