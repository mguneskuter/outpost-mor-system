package com.outpost.integration.psp.simulator.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.account.AccountTypes;
import com.outpost.framework.persistence.EnableOutpostPersistence;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.integration.psp.simulator.repository.PspConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = MybatisPspConfigurationRepositoryTest.TestApplication.class)
class MybatisPspConfigurationRepositoryTest {
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PspConfigurationRepositoryMapper mapper;

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    PostgresTestDatabase.registerDataSourceProperties(registry);
  }

  @BeforeEach
  void migrateAndSeedPspConfiguration() {
    Flyway.configure()
        .dataSource(jdbcTemplate.getDataSource())
        .locations("filesystem:" + System.getProperty("outpost.migration.location"))
        .cleanDisabled(false)
        .load()
        .clean();
    Flyway.configure()
        .dataSource(jdbcTemplate.getDataSource())
        .locations("filesystem:" + System.getProperty("outpost.migration.location"))
        .load()
        .migrate();
    long pspAccountTypeId = AccountTypes.PSP.getValue().getAccountTypeId();
    jdbcTemplate.update(
        "INSERT INTO account_type (account_type_id, code) VALUES (?, ?)", pspAccountTypeId, "PSP");
    jdbcTemplate.update(
        "INSERT INTO account (account_type_id, code, name, is_active, created_ts) "
            + "VALUES (?, ?, ?, true, now())",
        pspAccountTypeId,
        "DEMO_PSP",
        "Demo PSP");
    jdbcTemplate.update(
        "INSERT INTO psp_configuration "
            + "(account_id, account_type_id, base_url, api_key, hmac_secret) "
            + "SELECT account_id, account_type_id, ?, ?, ? FROM account WHERE code = ?",
        "http://simulator",
        "key",
        "hmac",
        "DEMO_PSP");
  }

  @Test
  void findsPspByCodeAndReturnsEmptyForUnknownCode() {
    var repository = new MybatisPspConfigurationRepository(mapper, 12, 34);

    assertThat(repository.findByCode("DEMO_PSP"))
        .get()
        .extracting(PspConfiguration::baseUrl, PspConfiguration::apiKey)
        .containsExactly("http://simulator", "key");
    assertThat(repository.findByCode("UNKNOWN")).isEmpty();
  }

  @SpringBootApplication
  @EnableOutpostPersistence(
      mapperPackages = "com.outpost.integration.psp.simulator.repository.mybatis")
  static class TestApplication {}
}
