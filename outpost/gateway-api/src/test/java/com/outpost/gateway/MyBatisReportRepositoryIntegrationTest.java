package com.outpost.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.framework.persistence.EnableOutpostPersistence;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.gateway.report.repository.ReportRepository;
import com.outpost.gateway.report.repository.mybatis.MyBatisReportRepository;
import com.outpost.gateway.report.repository.mybatis.ReportMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = MyBatisReportRepositoryIntegrationTest.TestApplication.class)
class MyBatisReportRepositoryIntegrationTest {

  private static final long MERCHANT_ACCOUNT_ID = 100L;
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_report_repository", "outpost_report_repository", "outpost_report_repository");

  @Autowired private ReportRepository repository;

  @BeforeAll
  static void migrateAndSeed() {
    DATABASE.start();
    Flyway.configure()
        .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
        .locations("filesystem:" + migrationLocation())
        .load()
        .migrate();
    JdbcTemplate seed =
        new JdbcTemplate(
            DataSourceBuilder.create()
                .url(DATABASE.getJdbcUrl())
                .username(DATABASE.getUsername())
                .password(DATABASE.getPassword())
                .build());
    reassignAccountTypeIdentifiers(seed);
    seed.update(
        "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
            + "SELECT ?, account_type_id, 'merchant-account', 'Merchant', true, now() "
            + "FROM account_type WHERE code = 'MERCHANT'",
        MERCHANT_ACCOUNT_ID);
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
    registry.add("spring.datasource.username", DATABASE::getUsername);
    registry.add("spring.datasource.password", DATABASE::getPassword);
  }

  @Test
  void findsMerchantCodeByAccountTypeCodeRegardlessOfSeededIdentifier() {
    var code = repository.findMerchantCode(MERCHANT_ACCOUNT_ID);

    assertThat(code).contains("merchant-account");
  }

  private static void reassignAccountTypeIdentifiers(JdbcTemplate seed) {
    seed.update("DELETE FROM account_type");
    seed.update("INSERT INTO account_type VALUES (4, 'MERCHANT'), (2, 'PSP')");
  }

  private static String migrationLocation() {
    return System.getProperty("outpost.migration.location");
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EnableOutpostPersistence
  static class TestApplication {
    @Bean
    ReportRepository reportRepository(ReportMapper mapper) {
      return new MyBatisReportRepository(mapper);
    }
  }
}
