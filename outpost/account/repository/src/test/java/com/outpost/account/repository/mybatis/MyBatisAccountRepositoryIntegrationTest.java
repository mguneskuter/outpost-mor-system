package com.outpost.account.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.account.AccountTypes;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class MyBatisAccountRepositoryIntegrationTest {
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_account_repository", "outpost_account_repository", "outpost_account_repository");
  private static @Nullable HikariDataSource dataSource;
  private static @Nullable MyBatisAccountRepository accounts;
  private static long rootAccountId;
  private static long merchantAccountId;
  private static long orphanAccountId;

  @BeforeAll
  static void migrateAndSeed() throws Exception {
    DATABASE.start();
    HikariDataSource database =
        DataSourceBuilder.create()
            .type(HikariDataSource.class)
            .url(DATABASE.getJdbcUrl())
            .username(DATABASE.getUsername())
            .password(DATABASE.getPassword())
            .build();
    dataSource = database;
    Flyway.configure()
        .dataSource(database)
        .locations("filesystem:" + migrationLocation())
        .load()
        .migrate();
    SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
    sessionFactory.setDataSource(database);
    sessionFactory.setMapperLocations(new ClassPathResource("db/mapper/account/AccountMapper.xml"));
    accounts =
        new MyBatisAccountRepository(
            new SqlSessionTemplate(Objects.requireNonNull(sessionFactory.getObject())));
    JdbcTemplate jdbc = new JdbcTemplate(database);
    for (AccountTypes type : List.of(AccountTypes.ROOT, AccountTypes.MERCHANT)) {
      jdbc.update(
          "INSERT INTO account_type (account_type_id, code) VALUES (?, ?)",
          type.getValue().getAccountTypeId(),
          type.getValue().getCode());
    }
    rootAccountId = account(jdbc, AccountTypes.ROOT, "ROOT", null);
    merchantAccountId = account(jdbc, AccountTypes.MERCHANT, "PARENTED_MERCHANT", rootAccountId);
    orphanAccountId = account(jdbc, AccountTypes.MERCHANT, "ORPHAN_MERCHANT", null);
  }

  @AfterAll
  static void closeDatabase() {
    if (dataSource != null) {
      dataSource.close();
    }
    DATABASE.stop();
  }

  @Test
  void loadsAnAccountWithItsParent() {
    var merchant = accounts().findAccountById(merchantAccountId).orElseThrow();

    assertThat(merchant.getAccountType()).isEqualTo(AccountTypes.MERCHANT.getValue());
    assertThat(merchant.getParentAccount())
        .hasValueSatisfying(parent -> assertThat(parent.getAccountId()).isEqualTo(rootAccountId));
    assertThat(accounts().findAccountByCode("PARENTED_MERCHANT")).contains(merchant);
  }

  @Test
  void rejectsStoredMerchantAccountWithoutParent() {
    assertThatThrownBy(() -> accounts().findAccountById(orphanAccountId))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void findsNothingForAnUnknownIdOrCode() {
    assertThat(accounts().findAccountById(Long.MAX_VALUE)).isEmpty();
    assertThat(accounts().findAccountByCode("UNKNOWN_ACCOUNT")).isEmpty();
  }

  private static MyBatisAccountRepository accounts() {
    return Objects.requireNonNull(accounts);
  }

  private static long account(
      JdbcTemplate jdbc, AccountTypes type, String code, @Nullable Long parentAccountId) {
    return Objects.requireNonNull(
        jdbc.queryForObject(
            "INSERT INTO account "
                + "(account_type_id, parent_account_id, code, name, is_active, created_ts) "
                + "VALUES (?, ?, ?, ?, true, now()) RETURNING account_id",
            Long.class,
            type.getValue().getAccountTypeId(),
            parentAccountId,
            code,
            code));
  }

  private static String migrationLocation() {
    String location = System.getProperty("outpost.migration.location");
    if (location == null || location.isBlank()) {
      throw new IllegalStateException("outpost.migration.location is required");
    }
    return location;
  }
}
