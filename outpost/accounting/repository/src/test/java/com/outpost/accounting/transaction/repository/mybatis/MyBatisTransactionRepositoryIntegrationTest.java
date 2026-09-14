package com.outpost.accounting.transaction.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.account.AccountTypes.AccountType;
import com.outpost.account.repository.AccountRepository;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionTypes;
import com.outpost.accounting.transaction.PaymentDetail;
import com.outpost.accounting.transaction.RefundDetail;
import com.outpost.accounting.transaction.Transaction;
import com.outpost.accounting.transaction.TransactionEvent;
import com.outpost.accounting.transaction.repository.TransactionRepository;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Currencies;
import com.outpost.framework.persistence.EnableOutpostPersistence;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.payment.common.Amount;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(classes = MyBatisTransactionRepositoryIntegrationTest.TestApplication.class)
class MyBatisTransactionRepositoryIntegrationTest {
  private static final Instant CREATED = Instant.parse("2026-09-13T10:00:00Z");
  private static final Account ROOT =
      Account.of(1L, AccountTypes.ROOT.getValue(), "ROOT", "Root", true, CREATED, null);
  private static final Account MERCHANT =
      Account.of(200L, AccountTypes.MERCHANT.getValue(), "SHOP", "Shop", true, CREATED, ROOT);
  private static final Account PSP =
      Account.of(300L, AccountTypes.PSP.getValue(), "PSP", "PSP", true, CREATED, ROOT);

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TransactionRepository repository;

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    PostgresTestDatabase.registerDataSourceProperties(registry);
  }

  @BeforeEach
  void migrateAndSeed() {
    DataSource dataSource = Objects.requireNonNull(jdbcTemplate.getDataSource());
    String location = "filesystem:" + System.getProperty("outpost.migration.location");
    Flyway.configure()
        .dataSource(dataSource)
        .locations(location)
        .cleanDisabled(false)
        .load()
        .clean();
    Flyway.configure().dataSource(dataSource).locations(location).load().migrate();
    seedReferenceDataAndAccounts();
  }

  @Test
  void storesPaymentWithItsDetailAndFindsItByReference() {
    PaymentDetail stored =
        repository.insertPaymentDetail(requestedPayment("payment-1")).orElseThrow();

    assertThat(stored.getPaymentTransaction().getTransactionId()).isPresent();
    assertThat(stored.getPaymentTransaction().getCreatedAt()).isPresent();
    assertThat(repository.findPaymentDetailByReference("payment-1")).contains(stored);
    assertThat(repository.findPaymentDetailByReferenceForUpdate("payment-1")).contains(stored);
  }

  @Test
  void answersEmptyForPaymentWhoseReferenceIsTaken() {
    PaymentDetail first =
        repository.insertPaymentDetail(requestedPayment("payment-1")).orElseThrow();

    assertThat(repository.insertPaymentDetail(requestedPayment("payment-1"))).isEmpty();
    assertThat(repository.findPaymentDetailByReference("payment-1")).contains(first);
  }

  @Test
  void storesEachEventTypeOfTransactionOnce() {
    Transaction payment = storedPayment("payment-1");

    Optional<TransactionEvent> first =
        repository.insertTransactionEvent(payment, TransactionEventTypes.ORDER_CREATED.getValue());
    Optional<TransactionEvent> repeat =
        repository.insertTransactionEvent(payment, TransactionEventTypes.ORDER_CREATED.getValue());

    assertThat(first).isPresent();
    assertThat(repeat).isEmpty();
    assertThat(repository.findTransactionEvents(payment)).containsExactly(first.orElseThrow());
  }

  @Test
  void findsTheEventOfThePaymentsCapture() {
    Transaction payment = storedPayment("payment-1");
    assertThat(repository.findCaptureTransactionEventByPayment(payment)).isEmpty();
    Transaction capture =
        repository
            .insertTransaction(
                Transaction.childOf(
                    payment,
                    null,
                    TransactionTypes.CAPTURE.getValue(),
                    MERCHANT,
                    "capture-1",
                    payment.getAmount(),
                    null))
            .orElseThrow();
    repository.insertTransactionEvent(capture, TransactionEventTypes.CAPTURED.getValue());

    TransactionEvent captured =
        repository.findCaptureTransactionEventByPayment(payment).orElseThrow();

    assertThat(captured.getTransactionEventType())
        .isEqualTo(TransactionEventTypes.CAPTURED.getValue());
    assertThat(captured.getTransaction()).isEqualTo(capture);
    assertThat(captured.getTransaction().getParentTransaction()).contains(payment);
  }

  @Test
  void storesRefundWithItsDetailAndFindsItByReferenceAndByPayment() {
    PaymentDetail payment =
        repository.insertPaymentDetail(requestedPayment("payment-1")).orElseThrow();
    Transaction paymentTransaction = payment.getPaymentTransaction();
    RefundDetail requested =
        new RefundDetail(
            Transaction.childOf(
                paymentTransaction,
                null,
                TransactionTypes.REFUND.getValue(),
                MERCHANT,
                "refund-1",
                paymentTransaction.getAmount(),
                null),
            payment.getNetAmount(),
            payment.getTaxAmount());

    RefundDetail stored = repository.insertRefundDetail(requested).orElseThrow();

    assertThat(stored.getRefundTransaction().getParentTransaction()).contains(paymentTransaction);
    assertThat(repository.findRefundDetailByReference("refund-1")).contains(stored);
    assertThat(repository.findRefundDetailsByPayment(paymentTransaction)).containsExactly(stored);
  }

  private Transaction storedPayment(String reference) {
    return repository
        .insertPaymentDetail(requestedPayment(reference))
        .orElseThrow()
        .getPaymentTransaction();
  }

  private static PaymentDetail requestedPayment(String reference) {
    return new PaymentDetail(
        Transaction.of(
            null, TransactionTypes.PAYMENT.getValue(), MERCHANT, reference, eur(12_000L), null),
        Countries.GERMANY.getValue(),
        null,
        PSP,
        eur(10_000L),
        eur(2_000L));
  }

  private static Amount eur(long quantity) {
    return new Amount(Currencies.EUR.getValue(), quantity);
  }

  /** Event types are seeded as needing no journal entry, so an event can be stored on its own. */
  private void seedReferenceDataAndAccounts() {
    for (AccountTypes type : new AccountTypes[] {AccountTypes.MERCHANT, AccountTypes.PSP}) {
      jdbcTemplate.update(
          "INSERT INTO account_type (account_type_id, code) VALUES (?, ?)",
          type.getValue().getAccountTypeId(),
          type.getValue().getCode());
    }
    jdbcTemplate.update(
        "INSERT INTO currency (currency_id, currency_code, exponent) VALUES (?, ?, ?)",
        Currencies.EUR.getValue().getCurrencyId(),
        Currencies.EUR.getValue().getCurrencyCode(),
        Currencies.EUR.getValue().getExponent());
    jdbcTemplate.update(
        "INSERT INTO country (country_id, iso_code, name) VALUES (?, ?, 'Germany')",
        Countries.GERMANY.getValue().getCountryId(),
        Countries.GERMANY.getValue().getIsoCode());
    for (TransactionTypes type :
        new TransactionTypes[] {
          TransactionTypes.PAYMENT, TransactionTypes.CAPTURE, TransactionTypes.REFUND
        }) {
      jdbcTemplate.update(
          "INSERT INTO transaction_type (transaction_type_id, code) VALUES (?, ?)",
          type.getValue().getTransactionTypeId(),
          type.getValue().getCode());
    }
    for (TransactionEventTypes type :
        new TransactionEventTypes[] {
          TransactionEventTypes.ORDER_CREATED, TransactionEventTypes.CAPTURED
        }) {
      jdbcTemplate.update(
          "INSERT INTO transaction_event_type "
              + "(transaction_event_type_id, code, requires_journal_entry) VALUES (?, ?, false)",
          type.getValue().getTransactionEventTypeId(),
          type.getValue().getCode());
    }
    for (Account account : new Account[] {MERCHANT, PSP}) {
      jdbcTemplate.update(
          "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
              + "VALUES (?, ?, ?, ?, true, ?)",
          account.getAccountId(),
          account.getAccountType().getAccountTypeId(),
          account.getCode(),
          account.getName(),
          Timestamp.from(CREATED));
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EnableOutpostPersistence
  static class TestApplication {
    @Bean
    TransactionRepository transactionRepository(
        SqlSessionTemplate sqlSessionTemplate, PlatformTransactionManager transactionManager) {
      return new MyBatisTransactionRepository(
          sqlSessionTemplate, transactionManager, new SeededAccounts());
    }
  }

  /** The merchant and PSP accounts the test seeds, found by id. */
  private static final class SeededAccounts implements AccountRepository {
    @Override
    public Optional<Account> findAccountById(long accountId) {
      return Stream.of(MERCHANT, PSP)
          .filter(account -> account.getAccountId() == accountId)
          .findFirst();
    }

    @Override
    public Optional<Account> findAccountByCode(String code) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Account> findTaxAuthorityAccountByCountryId(long countryId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Account> findAccountByAccountType(AccountType accountType) {
      throw new UnsupportedOperationException();
    }
  }
}
