package com.outpost.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.common.iso.Currencies;
import com.outpost.framework.persistence.EnableOutpostPersistence;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.ledger.payment.repository.PaymentRepository;
import com.outpost.ledger.payment.repository.RefundChild;
import com.outpost.ledger.payment.repository.mybatis.MyBatisPaymentRepository;
import com.outpost.ledger.payment.repository.mybatis.PaymentMapper;
import java.time.Instant;
import java.util.List;
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

@SpringBootTest(classes = MyBatisPaymentRepositoryIntegrationTest.TestApplication.class)
class MyBatisPaymentRepositoryIntegrationTest {

  private static final long PAYMENT_TRANSACTION_ID = 200L;
  private static final long MERCHANT_ACCOUNT_ID = 100L;
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_payment_repository", "outpost_payment_repository", "outpost_payment_repository");

  @Autowired private PaymentRepository repository;

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
    LedgerStaticDataFixtures.materialize(seed);
    reassignRefundTransactionTypeIdentifier(seed);
    seedPayment(seed);
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
    registry.add("spring.datasource.username", DATABASE::getUsername);
    registry.add("spring.datasource.password", DATABASE::getPassword);
  }

  @Test
  void refundChildIsFoundByTransactionTypeCodeRegardlessOfSeededIdentifier() {
    long eurCurrencyId = Currencies.EUR.getValue().getCurrencyId();
    Long refundId =
        repository.insertRefundTransaction(
            PAYMENT_TRANSACTION_ID,
            MERCHANT_ACCOUNT_ID,
            "refund-ref",
            200L,
            eurCurrencyId,
            Instant.now());
    repository.insertRefundDetail(refundId, 180L, 20L);

    List<RefundChild> children = repository.findRefundChildren(PAYMENT_TRANSACTION_ID);

    assertThat(children).hasSize(1);
    assertThat(children.get(0).quantity()).isEqualTo(200L);
  }

  private static void reassignRefundTransactionTypeIdentifier(JdbcTemplate seed) {
    seed.update("DELETE FROM transaction_type");
    seed.update(
        "INSERT INTO transaction_type VALUES (1, 'PAYMENT'), (2, 'CAPTURE'), (99, 'REFUND')");
  }

  private static void seedPayment(JdbcTemplate seed) {
    seed.update(
        "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
            + "SELECT ?, account_type_id, 'merchant', 'Merchant', true, now() "
            + "FROM account_type WHERE code = 'MERCHANT'",
        MERCHANT_ACCOUNT_ID);
    seed.update(
        "INSERT INTO transaction (transaction_id, transaction_type_id, account_id, reference, "
            + "quantity, currency_id, created_ts) "
            + "SELECT ?, transaction_type_id, ?, 'payment-ref', 1000, "
            + "(SELECT currency_id FROM currency WHERE currency_code = 'EUR'), now() "
            + "FROM transaction_type WHERE code = 'PAYMENT'",
        PAYMENT_TRANSACTION_ID,
        MERCHANT_ACCOUNT_ID);
  }

  private static String migrationLocation() {
    return System.getProperty("outpost.migration.location");
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EnableOutpostPersistence
  static class TestApplication {
    @Bean
    PaymentRepository paymentRepository(PaymentMapper mapper) {
      return new MyBatisPaymentRepository(mapper);
    }
  }
}
