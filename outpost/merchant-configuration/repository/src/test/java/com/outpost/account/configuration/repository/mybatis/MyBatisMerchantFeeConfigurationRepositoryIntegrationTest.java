package com.outpost.account.configuration.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.account.AccountTypes;
import com.outpost.account.configuration.FeeModes;
import com.outpost.common.iso.Currencies;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MyBatisMerchantFeeConfigurationRepositoryIntegrationTest {
  private static @Nullable MerchantConfigurationDatabase database;
  private static @Nullable MyBatisMerchantFeeConfigurationRepository feeConfigurations;
  private static long configuredMerchantAccountId;
  private static long unconfiguredMerchantAccountId;

  @BeforeAll
  static void migrateAndSeed() throws Exception {
    MerchantConfigurationDatabase migrated =
        new MerchantConfigurationDatabase(
            "outpost_merchant_fee", "db/mapper/configuration/MerchantFeeConfigurationMapper.xml");
    database = migrated;
    feeConfigurations = new MyBatisMerchantFeeConfigurationRepository(migrated.sqlSession());
    JdbcTemplate jdbc = migrated.jdbc();
    for (Currencies currency : List.of(Currencies.EUR, Currencies.USD)) {
      jdbc.update(
          "INSERT INTO currency (currency_id, currency_code, exponent) VALUES (?, ?, ?)",
          currency.getValue().getCurrencyId(),
          currency.getValue().getCurrencyCode(),
          currency.getValue().getExponent());
    }
    var percentage = FeeModes.PERCENTAGE.getValue();
    jdbc.update(
        "INSERT INTO fee_mode (fee_mode_id, code) VALUES (?, ?)",
        percentage.getFeeModeId(),
        percentage.getCode());
    configuredMerchantAccountId = migrated.account(AccountTypes.MERCHANT, "CONFIGURED_MERCHANT");
    unconfiguredMerchantAccountId =
        migrated.account(AccountTypes.MERCHANT, "UNCONFIGURED_MERCHANT");
    jdbc.update(
        "INSERT INTO merchant_fee_configuration "
            + "(account_id, account_type_id, currency_id, fee_mode_id, fee_rate_bps) "
            + "VALUES (?, ?, ?, ?, 0)",
        configuredMerchantAccountId,
        AccountTypes.MERCHANT.getValue().getAccountTypeId(),
        Currencies.EUR.getValue().getCurrencyId(),
        percentage.getFeeModeId());
  }

  @AfterAll
  static void closeDatabase() {
    if (database != null) {
      database.close();
    }
  }

  @Test
  void merchantWithFeeTermsInTheCurrencyHasFeeConfiguration() {
    assertThat(
            feeConfigurations()
                .hasFeeConfiguration(configuredMerchantAccountId, Currencies.EUR.getValue()))
        .isTrue();
  }

  @Test
  void feeTermsInAnotherCurrencyAreNotFeeConfigurationForThisCurrency() {
    assertThat(
            feeConfigurations()
                .hasFeeConfiguration(configuredMerchantAccountId, Currencies.USD.getValue()))
        .isFalse();
  }

  @Test
  void merchantWithoutFeeTermsHasNoFeeConfiguration() {
    assertThat(
            feeConfigurations()
                .hasFeeConfiguration(unconfiguredMerchantAccountId, Currencies.EUR.getValue()))
        .isFalse();
  }

  private static MyBatisMerchantFeeConfigurationRepository feeConfigurations() {
    return Objects.requireNonNull(feeConfigurations);
  }
}
