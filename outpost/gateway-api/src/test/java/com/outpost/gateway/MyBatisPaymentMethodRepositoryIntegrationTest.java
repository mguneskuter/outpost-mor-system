package com.outpost.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.gateway.paymentmethod.repository.PaymentMethodRepository;
import java.sql.Connection;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = GatewayApiApplication.class)
class MyBatisPaymentMethodRepositoryIntegrationTest {
  private static final long MERCHANT_ACCOUNT_ID = 400L;
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_gateway_payment_method",
          "outpost_gateway_payment_method",
          "outpost_gateway_payment_method");

  @Autowired private PaymentMethodRepository repository;

  @BeforeAll
  static void migrateAndSeed() throws SQLException {
    DATABASE.start();
    Flyway.configure()
        .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
        .locations("filesystem:" + migrationLocation())
        .schemas("public")
        .defaultSchema("public")
        .load()
        .migrate();
    JdbcTemplate seed =
        new JdbcTemplate(
            DataSourceBuilder.create()
                .url(DATABASE.getJdbcUrl())
                .username(DATABASE.getUsername())
                .password(DATABASE.getPassword())
                .build());
    GatewayStaticDataFixtures.materializeAll(seed);
    try (Connection connection = DATABASE.createConnection("")) {
      execute(
          connection,
          "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
              + "VALUES (400, 2, 'PM_MERCHANT', 'Payment method merchant', true, now()), "
              + "(401, 2, 'PM_OTHER_MERCHANT', 'Other merchant', true, now()), "
              + "(402, 4, 'PM_PSP_ONE', 'PSP One', true, now()), "
              + "(403, 4, 'PM_PSP_TWO', 'PSP Two', true, now())");
      execute(
          connection,
          "INSERT INTO merchant_psp (account_id, psp_account_id) VALUES (400, 402), (400, 403), "
              + "(401, 402)");
    }
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
    registry.add("spring.datasource.username", DATABASE::getUsername);
    registry.add("spring.datasource.password", DATABASE::getPassword);
    registry.add(
        "OUTPOST_HMAC_ENCRYPTION_KEY", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    registry.add("OUTPOST_OPERATOR_API_KEY", () -> "integration-operator-key");
    registry.add("OUTPOST_LEDGER_GATEWAY_HMAC_SECRET", () -> "gateway-integration-test-key");
  }

  @Test
  void listsExactlyTheEnabledPsps() {
    var methods = repository.findEnabled(MERCHANT_ACCOUNT_ID);

    assertThat(methods)
        .extracting(PaymentMethodRepository.PaymentMethod::pspCode)
        .containsExactlyInAnyOrder("PM_PSP_ONE", "PM_PSP_TWO");
  }

  @Test
  void listsNothingForMerchantWithNoEnabledPsp() {
    var methods = repository.findEnabled(999L);

    assertThat(methods).isEmpty();
  }

  private static void execute(Connection connection, String sql) throws SQLException {
    try (var statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static String migrationLocation() {
    String location = System.getProperty("outpost.migration.location");
    if (location == null || location.isBlank()) {
      throw new IllegalStateException("outpost.migration.location is required");
    }
    return location;
  }
}
