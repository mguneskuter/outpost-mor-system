package com.outpost.account.configuration.repository.mybatis;

import com.outpost.account.Account;
import com.outpost.account.configuration.FeeModes;
import com.outpost.account.configuration.repository.MerchantFeeConfigurationRepository;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.payment.common.Amount;
import java.util.Optional;
import org.apache.ibatis.session.SqlSession;

/** Reads merchant fee terms from {@code merchant_fee_configuration}. */
public final class MyBatisMerchantFeeConfigurationRepository
    implements MerchantFeeConfigurationRepository {
  private final MerchantFeeConfigurationMapper mapper;

  /** Creates a repository over a Spring-managed {@code sqlSession}. */
  public MyBatisMerchantFeeConfigurationRepository(SqlSession sqlSession) {
    this.mapper = sqlSession.getMapper(MerchantFeeConfigurationMapper.class);
  }

  @Override
  public boolean hasFeeConfiguration(long merchantAccountId, Currency currency) {
    return mapper.hasFeeConfiguration(merchantAccountId, currency.getCurrencyCode());
  }

  @Override
  public Optional<com.outpost.account.configuration.MerchantFeeConfiguration>
      findMerchantFeeConfigurationByAccountAndCurrency(Account merchantAccount, Currency currency) {
    return Optional.ofNullable(
            mapper.findMerchantFeeConfigurationByAccountAndCurrency(
                merchantAccount.getAccountId(), currency.getCurrencyCode()))
        .map(
            stored -> {
              Long feeFixed = stored.feeFixed();
              return new com.outpost.account.configuration.MerchantFeeConfiguration(
                  stored.merchantFeeConfigurationId(),
                  merchantAccount,
                  currency,
                  FeeModes.fromCode(stored.feeMode())
                      .orElseThrow(
                          () -> new IllegalStateException("Stored fee mode is not supported")),
                  stored.feeRateBps(),
                  feeFixed == null ? null : new Amount(currency, feeFixed));
            });
  }
}
