package com.outpost.account.configuration.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.account.AccountTypes;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MyBatisMerchantPspRepositoryIntegrationTest {
  private static @Nullable MerchantConfigurationDatabase database;
  private static @Nullable MyBatisMerchantPspRepository merchantPsps;
  private static long merchantAccountId;
  private static long enabledPspAccountId;
  private static long otherPspAccountId;

  @BeforeAll
  static void migrateAndSeed() throws Exception {
    MerchantConfigurationDatabase migrated =
        new MerchantConfigurationDatabase(
            "outpost_merchant_psp", "db/mapper/configuration/MerchantPspMapper.xml");
    database = migrated;
    merchantPsps =
        new MyBatisMerchantPspRepository(migrated.sqlSession().getMapper(MerchantPspMapper.class));
    merchantAccountId = migrated.account(AccountTypes.MERCHANT, "PSP_MERCHANT");
    enabledPspAccountId = migrated.account(AccountTypes.PSP, "ENABLED_PSP");
    otherPspAccountId = migrated.account(AccountTypes.PSP, "OTHER_PSP");
    migrated
        .jdbc()
        .update(
            "INSERT INTO merchant_psp (account_id, psp_account_id) VALUES (?, ?)",
            merchantAccountId,
            enabledPspAccountId);
  }

  @AfterAll
  static void closeDatabase() {
    if (database != null) {
      database.close();
    }
  }

  @Test
  void pspAssociatedWithTheMerchantIsEnabled() {
    assertThat(merchantPsps().isPspEnabled(merchantAccountId, enabledPspAccountId)).isTrue();
  }

  @Test
  void pspNotAssociatedWithTheMerchantIsNotEnabled() {
    assertThat(merchantPsps().isPspEnabled(merchantAccountId, otherPspAccountId)).isFalse();
  }

  private static MyBatisMerchantPspRepository merchantPsps() {
    return Objects.requireNonNull(merchantPsps);
  }
}
