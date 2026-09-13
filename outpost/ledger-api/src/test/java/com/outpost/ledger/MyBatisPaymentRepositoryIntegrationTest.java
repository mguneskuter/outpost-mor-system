package com.outpost.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.common.iso.Currencies;
import com.outpost.framework.persistence.EnableOutpostPersistence;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.ledger.payment.repository.PaymentEvent;
import com.outpost.ledger.payment.repository.PaymentRepository;
import com.outpost.ledger.payment.repository.RefundChild;
import com.outpost.ledger.payment.repository.StoredTransaction;
import com.outpost.ledger.payment.repository.mybatis.MyBatisPaymentRepository;
import com.outpost.ledger.payment.repository.mybatis.PaymentMapper;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = MyBatisPaymentRepositoryIntegrationTest.TestApplication.class)
class MyBatisPaymentRepositoryIntegrationTest {

  private static final long PAYMENT_TRANSACTION_ID = 200L;
  private static final long MERCHANT_ACCOUNT_ID = 100L;
  private static final long PSP_ACCOUNT_ID = 101L;
  private static final long COUNTRY_ID = 1L;
  private static final long COUNTRY_SUBDIVISION_ID = 1L;
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_payment_repository", "outpost_payment_repository", "outpost_payment_repository");

  @Autowired private PaymentRepository repository;
  @Autowired private JdbcTemplate jdbcTemplate;

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
    reassignTransactionAndEventTypeIdentifiers(seed);
    seedPayment(seed);
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
    registry.add("spring.datasource.username", DATABASE::getUsername);
    registry.add("spring.datasource.password", DATABASE::getPassword);
  }

  @Test
  @Transactional
  void createdPaymentAndItsOrderCreatedEventCarryTheWritingTransactionTime() {
    StoredTransaction payment =
        repository.insertTransaction(
            MERCHANT_ACCOUNT_ID, "payment-dated", 1000L, Currencies.EUR.getValue().getCurrencyId());
    PaymentEvent orderCreated = repository.insertEvent(payment.transactionId());

    Instant transactionTime =
        Objects.requireNonNull(jdbcTemplate.queryForObject("SELECT now()", Instant.class));
    assertThat(payment.createdAt()).isEqualTo(transactionTime);
    assertThat(orderCreated.occurredAt()).isEqualTo(transactionTime);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT created_ts FROM transaction WHERE transaction_id = ?",
                Instant.class,
                payment.transactionId()))
        .isEqualTo(transactionTime);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT event_ts FROM transaction_event WHERE transaction_event_id = ?",
                Instant.class,
                orderCreated.transactionEventId()))
        .isEqualTo(transactionTime);
  }

  @Test
  void refundChildIsFoundByTransactionTypeCodeRegardlessOfSeededIdentifier() {
    long eurCurrencyId = Currencies.EUR.getValue().getCurrencyId();
    StoredTransaction refund =
        repository.insertRefundTransaction(
            PAYMENT_TRANSACTION_ID, MERCHANT_ACCOUNT_ID, "refund-ref", 200L, eurCurrencyId);
    repository.insertRefundDetail(refund.transactionId(), 180L, 20L);

    List<RefundChild> children = repository.findRefundChildren(PAYMENT_TRANSACTION_ID);

    assertThat(children).hasSize(1);
    assertThat(children.get(0).quantity()).isEqualTo(200L);
  }

  @Test
  @Transactional
  void paymentCaptureAndOrderCreatedStatementsResolveTypesByCodeRegardlessOfSeededIdentifiers() {
    long eurCurrencyId = Currencies.EUR.getValue().getCurrencyId();
    long paymentTransactionId =
        repository
            .insertTransaction(MERCHANT_ACCOUNT_ID, "payment-by-code", 1000L, eurCurrencyId)
            .transactionId();
    repository.insertPaymentDetail(
        paymentTransactionId, COUNTRY_ID, COUNTRY_SUBDIVISION_ID, PSP_ACCOUNT_ID, 900L, 100L);
    long orderCreatedEventId = repository.insertEvent(paymentTransactionId).transactionEventId();
    long captureTransactionId =
        repository
            .insertCaptureTransaction(
                paymentTransactionId, MERCHANT_ACCOUNT_ID, "capture-by-code", 1000L, eurCurrencyId)
            .transactionId();

    assertThat(eventTypeCodeOf(orderCreatedEventId)).isEqualTo("ORDER_CREATED");
    assertThat(repository.findPaymentFamilyForUpdate("payment-by-code").transactionId())
        .isEqualTo(paymentTransactionId);
    assertThat(repository.findCaptureChild(paymentTransactionId).transactionId())
        .isEqualTo(captureTransactionId);
    assertThat(repository.findCaptureByReference("capture-by-code").transactionId())
        .isEqualTo(captureTransactionId);
  }

  private String eventTypeCodeOf(long transactionEventId) {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT event_type.code FROM transaction_event event "
                + "JOIN transaction_event_type event_type "
                + "ON event_type.transaction_event_type_id = event.transaction_event_type_id "
                + "WHERE event.transaction_event_id = ?",
            String.class,
            transactionEventId));
  }

  private static void reassignTransactionAndEventTypeIdentifiers(JdbcTemplate seed) {
    seed.update("DELETE FROM transaction_type");
    seed.update(
        "INSERT INTO transaction_type VALUES (97, 'PAYMENT'), (98, 'CAPTURE'), (99, 'REFUND')");
    seed.update(
        "UPDATE transaction_event_type "
            + "SET transaction_event_type_id = transaction_event_type_id + 100");
  }

  private static void seedPayment(JdbcTemplate seed) {
    seed.update(
        "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
            + "SELECT ?, account_type_id, 'merchant', 'Merchant', true, now() "
            + "FROM account_type WHERE code = 'MERCHANT'",
        MERCHANT_ACCOUNT_ID);
    seed.update(
        "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
            + "SELECT ?, account_type_id, 'psp', 'PSP', true, now() "
            + "FROM account_type WHERE code = 'PSP'",
        PSP_ACCOUNT_ID);
    seed.update(
        "INSERT INTO country (country_id, iso_code, name) VALUES (?, 'NL', 'Netherlands')",
        COUNTRY_ID);
    seed.update(
        "INSERT INTO country_subdivision (country_subdivision_id, country_id, code, name) "
            + "VALUES (?, ?, 'NL-NH', 'Noord-Holland')",
        COUNTRY_SUBDIVISION_ID,
        COUNTRY_ID);
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
