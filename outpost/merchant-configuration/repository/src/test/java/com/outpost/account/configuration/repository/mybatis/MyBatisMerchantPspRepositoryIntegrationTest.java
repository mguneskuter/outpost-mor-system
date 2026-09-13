package com.outpost.account.configuration.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.account.repository.AccountRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MyBatisMerchantPspRepositoryIntegrationTest {
  private static @Nullable MerchantConfigurationDatabase database;
  private static @Nullable MyBatisMerchantPspRepository merchantPsps;
  private static long merchantAccountId;
  private static long enabledPspAccountId;
  private static long alsoEnabledPspAccountId;
  private static long otherPspAccountId;

  @BeforeAll
  static void migrateAndSeed() throws Exception {
    MerchantConfigurationDatabase migrated =
        new MerchantConfigurationDatabase(
            "outpost_merchant_psp", "db/mapper/configuration/MerchantPspMapper.xml");
    database = migrated;
    merchantAccountId = migrated.account(AccountTypes.MERCHANT, "PSP_MERCHANT");
    enabledPspAccountId = migrated.account(AccountTypes.PSP, "ENABLED_PSP");
    alsoEnabledPspAccountId = migrated.account(AccountTypes.PSP, "ALSO_ENABLED_PSP");
    otherPspAccountId = migrated.account(AccountTypes.PSP, "OTHER_PSP");
    InMemoryAccounts accounts = new InMemoryAccounts();
    accounts.psp(enabledPspAccountId, "ENABLED_PSP");
    accounts.psp(alsoEnabledPspAccountId, "ALSO_ENABLED_PSP");
    accounts.psp(otherPspAccountId, "OTHER_PSP");
    merchantPsps =
        new MyBatisMerchantPspRepository(
            migrated.sqlSession().getMapper(MerchantPspMapper.class), accounts);
    migrated
        .jdbc()
        .update(
            "INSERT INTO merchant_psp (account_id, psp_account_id) VALUES (?, ?), (?, ?)",
            merchantAccountId,
            enabledPspAccountId,
            merchantAccountId,
            alsoEnabledPspAccountId);
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

  @Test
  void listsTheEnabledPspsOrderedByCode() {
    assertThat(merchantPsps().findEnabledPsps(merchantAccountId))
        .extracting(Account::getCode)
        .containsExactly("ALSO_ENABLED_PSP", "ENABLED_PSP");
  }

  @Test
  void listsNoPspForMerchantWithoutEnabledPsps() {
    assertThat(merchantPsps().findEnabledPsps(otherPspAccountId)).isEmpty();
  }

  private static MyBatisMerchantPspRepository merchantPsps() {
    return Objects.requireNonNull(merchantPsps);
  }

  /** The PSP accounts the test stored, reachable by id like the account repository serves them. */
  private static final class InMemoryAccounts implements AccountRepository {
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private final Account root =
        Account.of(1L, AccountTypes.ROOT.getValue(), "ROOT", "Root", true, CREATED, null);
    private final Map<Long, Account> byId = new HashMap<>();

    private void psp(long accountId, String code) {
      byId.put(
          accountId,
          Account.of(accountId, AccountTypes.PSP.getValue(), code, code, true, CREATED, root));
    }

    @Override
    public Optional<Account> findAccountById(long accountId) {
      return Optional.ofNullable(byId.get(accountId));
    }

    @Override
    public Optional<Account> findAccountByCode(String code) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Account> findTaxAuthorityAccountByCountryId(long countryId) {
      throw new UnsupportedOperationException();
    }
  }
}
