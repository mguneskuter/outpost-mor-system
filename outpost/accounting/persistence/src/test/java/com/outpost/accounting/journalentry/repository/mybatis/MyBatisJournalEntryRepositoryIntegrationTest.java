package com.outpost.accounting.journalentry.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.accounting.JournalEntry;
import com.outpost.accounting.JournalEntryLine;
import com.outpost.accounting.JournalEntryTypes;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.Transaction;
import com.outpost.accounting.TransactionEvent;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionTypes;
import com.outpost.accounting.journalentry.repository.JournalEntryRepository;
import com.outpost.accounting.templates.PendingFeeJournalTemplates;
import com.outpost.common.iso.Currencies;
import com.outpost.framework.persistence.EnableOutpostPersistence;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.payment.common.Amount;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = MyBatisJournalEntryRepositoryIntegrationTest.TestApplication.class)
class MyBatisJournalEntryRepositoryIntegrationTest {
  private static final Instant NOW = Instant.parse("2026-09-13T10:00:00Z");
  private static final long PAYMENT_ID = 10L;
  private static final long ORDER_CREATED_EVENT_ID = 11L;

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JournalEntryRepository repository;
  @Autowired private PlatformTransactionManager transactionManager;

  private final Account root =
      Account.of(1L, AccountTypes.ROOT.getValue(), "ROOT", "Root", true, NOW, null);
  private final Account platform =
      Account.of(100L, AccountTypes.PLATFORM.getValue(), "OUTPOST", "Outpost", true, NOW, root);
  private final Account merchant =
      Account.of(200L, AccountTypes.MERCHANT.getValue(), "SHOP", "Shop", true, NOW, root);
  private final Register merchantPendingFee =
      new Register(20008L, merchant, RegisterTypes.PENDING_FEE.getValue());
  private final Register platformPendingFee =
      new Register(10008L, platform, RegisterTypes.PENDING_FEE.getValue());

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
    seedAccountsAndRegisters();
  }

  @Test
  void returnsTheStoredEntryCarryingTheStoredEntryAndLineIds() {
    JournalEntry stored =
        new TransactionTemplate(transactionManager)
            .execute(
                status -> {
                  insertPaymentWithOrderCreatedEvent();
                  return repository.insertJournalEntry(pendingFeeEntry(merchantPendingFee));
                });

    Long storedEntryId =
        jdbcTemplate.queryForObject(
            "SELECT journal_entry_id FROM journal_entry WHERE transaction_event_id = ?",
            Long.class,
            ORDER_CREATED_EVENT_ID);
    assertThat(Objects.requireNonNull(stored).getJournalEntryId()).contains(storedEntryId);
    assertThat(stored.getJournalEntryLines())
        .extracting(line -> line.getJournalEntryLineId().orElseThrow())
        .containsExactlyElementsOf(
            jdbcTemplate.queryForList(
                "SELECT journal_entry_line_id FROM journal_entry_line WHERE journal_entry_id = ? "
                    + "ORDER BY journal_entry_line_id",
                Long.class,
                storedEntryId));
    assertThat(stored.getJournalEntryLines())
        .extracting(JournalEntryLine::getRegister, line -> line.getAmount().quantity())
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(merchantPendingFee, 500L),
            org.assertj.core.groups.Tuple.tuple(platformPendingFee, -500L));
  }

  @Test
  void lineThatCannotBeStoredLeavesNoEntry() {
    // The event commits before its entry exists; with the event-side entry-count check off, only
    // the repository call decides whether an entry row survives.
    jdbcTemplate.execute(
        "ALTER TABLE transaction_event DISABLE TRIGGER transaction_event_entry_coupling");
    try {
      insertPaymentWithOrderCreatedEvent();
      Register missingRegister =
          new Register(99_999L, merchant, RegisterTypes.PENDING_FEE.getValue());

      assertThatThrownBy(() -> repository.insertJournalEntry(pendingFeeEntry(missingRegister)))
          .isInstanceOf(DataAccessException.class);

      assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM journal_entry", Long.class))
          .isZero();
    } finally {
      jdbcTemplate.execute(
          "ALTER TABLE transaction_event ENABLE TRIGGER transaction_event_entry_coupling");
    }
  }

  private JournalEntry pendingFeeEntry(Register merchantRegister) {
    Transaction payment =
        Transaction.of(
            PAYMENT_ID,
            TransactionTypes.PAYMENT.getValue(),
            merchant,
            "payment-1",
            new Amount(Currencies.EUR.getValue(), 12_000L),
            NOW);
    TransactionEvent orderCreated =
        new TransactionEvent(
            ORDER_CREATED_EVENT_ID, payment, TransactionEventTypes.ORDER_CREATED.getValue(), NOW);
    return PendingFeeJournalTemplates.FEE_PENDING.build(
        orderCreated,
        merchantRegister,
        platformPendingFee,
        new Amount(Currencies.EUR.getValue(), 500L),
        NOW);
  }

  private void seedAccountsAndRegisters() {
    for (AccountTypes type : new AccountTypes[] {AccountTypes.PLATFORM, AccountTypes.MERCHANT}) {
      jdbcTemplate.update(
          "INSERT INTO account_type (account_type_id, code) VALUES (?, ?)",
          type.getValue().getAccountTypeId(),
          type.getValue().getCode());
    }
    RegisterTypes.RegisterType pendingFee = RegisterTypes.PENDING_FEE.getValue();
    jdbcTemplate.update(
        "INSERT INTO register_type (register_type_id, register_type_code) VALUES (?, ?)",
        pendingFee.getRegisterTypeId(),
        pendingFee.getCode());
    jdbcTemplate.update(
        "INSERT INTO currency (currency_id, currency_code, exponent) VALUES (?, ?, ?)",
        Currencies.EUR.getValue().getCurrencyId(),
        Currencies.EUR.getValue().getCurrencyCode(),
        Currencies.EUR.getValue().getExponent());
    jdbcTemplate.update(
        "INSERT INTO transaction_type (transaction_type_id, code) VALUES (?, ?)",
        TransactionTypes.PAYMENT.getValue().getTransactionTypeId(),
        TransactionTypes.PAYMENT.getValue().getCode());
    jdbcTemplate.update(
        "INSERT INTO transaction_event_type "
            + "(transaction_event_type_id, code, requires_journal_entry) VALUES (?, ?, true)",
        TransactionEventTypes.ORDER_CREATED.getValue().getTransactionEventTypeId(),
        TransactionEventTypes.ORDER_CREATED.getValue().getCode());
    jdbcTemplate.update(
        "INSERT INTO journal_entry_type (journal_entry_type_id, code) VALUES (?, ?)",
        JournalEntryTypes.FEE_PENDING.getValue().getJournalEntryTypeId(),
        JournalEntryTypes.FEE_PENDING.getValue().getCode());
    for (Account account : new Account[] {platform, merchant}) {
      jdbcTemplate.update(
          "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
              + "VALUES (?, ?, ?, ?, true, ?)",
          account.getAccountId(),
          account.getAccountType().getAccountTypeId(),
          account.getCode(),
          account.getName(),
          Timestamp.from(NOW));
    }
    for (Register register : new Register[] {platformPendingFee, merchantPendingFee}) {
      jdbcTemplate.update(
          "INSERT INTO register (register_id, account_id, register_type_id) VALUES (?, ?, ?)",
          register.getRegisterId(),
          register.getAccount().getAccountId(),
          register.getRegisterType().getRegisterTypeId());
    }
  }

  private void insertPaymentWithOrderCreatedEvent() {
    jdbcTemplate.update(
        "INSERT INTO transaction (transaction_id, transaction_type_id, account_id, reference, "
            + "quantity, currency_id, created_ts) VALUES (?, ?, ?, 'payment-1', 12000, ?, ?)",
        PAYMENT_ID,
        TransactionTypes.PAYMENT.getValue().getTransactionTypeId(),
        merchant.getAccountId(),
        Currencies.EUR.getValue().getCurrencyId(),
        Timestamp.from(NOW));
    jdbcTemplate.update(
        "INSERT INTO transaction_event (transaction_event_id, transaction_id, "
            + "transaction_event_type_id, event_ts) VALUES (?, ?, ?, ?)",
        ORDER_CREATED_EVENT_ID,
        PAYMENT_ID,
        TransactionEventTypes.ORDER_CREATED.getValue().getTransactionEventTypeId(),
        Timestamp.from(NOW));
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EnableOutpostPersistence
  static class TestApplication {
    @Bean
    JournalEntryRepository journalEntryRepository(
        JournalEntryMapper mapper, PlatformTransactionManager transactionManager) {
      return new MyBatisJournalEntryRepository(mapper, transactionManager);
    }
  }
}
