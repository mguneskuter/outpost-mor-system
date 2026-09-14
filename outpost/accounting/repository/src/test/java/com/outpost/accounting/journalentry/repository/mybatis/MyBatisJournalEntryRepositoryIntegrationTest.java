package com.outpost.accounting.journalentry.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.account.AccountTypes.AccountType;
import com.outpost.account.repository.AccountRepository;
import com.outpost.accounting.JournalEntryTypes;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionTypes;
import com.outpost.accounting.journalentry.CaptureRegisters;
import com.outpost.accounting.journalentry.JournalEntry;
import com.outpost.accounting.journalentry.JournalEntryLine;
import com.outpost.accounting.journalentry.PendingFee;
import com.outpost.accounting.journalentry.repository.JournalEntryRepository;
import com.outpost.accounting.templates.CaptureJournalTemplates;
import com.outpost.accounting.templates.PendingFeeJournalTemplates;
import com.outpost.accounting.transaction.PaymentDetail;
import com.outpost.accounting.transaction.Transaction;
import com.outpost.accounting.transaction.TransactionEvent;
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
  private static final long CAPTURE_ID = 20L;
  private static final long CAPTURED_EVENT_ID = 21L;
  private static final Account ROOT =
      Account.of(1L, AccountTypes.ROOT.getValue(), "ROOT", "Root", true, NOW, null);
  private static final Account PLATFORM =
      Account.of(100L, AccountTypes.PLATFORM.getValue(), "OUTPOST", "Outpost", true, NOW, ROOT);
  private static final Account MERCHANT =
      Account.of(200L, AccountTypes.MERCHANT.getValue(), "SHOP", "Shop", true, NOW, ROOT);
  private static final Account PSP =
      Account.of(300L, AccountTypes.PSP.getValue(), "PSP", "PSP", true, NOW, ROOT);
  private static final Account TAX_AUTHORITY =
      Account.of(1006L, AccountTypes.TAX_AUTHORITY.getValue(), "TAX_DE", "Tax DE", true, NOW, ROOT);

  private static final Register MERCHANT_PENDING_FEE =
      new Register(20008L, MERCHANT, RegisterTypes.PENDING_FEE.getValue());
  private static final Register PLATFORM_PENDING_FEE =
      new Register(10008L, PLATFORM, RegisterTypes.PENDING_FEE.getValue());
  private static final CaptureRegisters CAPTURE_REGISTERS =
      new CaptureRegisters(
          new Register(30003L, PSP, RegisterTypes.PSP_RECEIVABLE.getValue()),
          new Register(100604L, TAX_AUTHORITY, RegisterTypes.TAX_PAYABLE.getValue()),
          new Register(20001L, MERCHANT, RegisterTypes.MERCHANT_PAYABLE.getValue()),
          new Register(10005L, PLATFORM, RegisterTypes.FEE_REVENUE.getValue()));

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JournalEntryRepository repository;
  @Autowired private PlatformTransactionManager transactionManager;

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
                  return repository.insertJournalEntry(pendingFeeEntry(MERCHANT_PENDING_FEE));
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
            org.assertj.core.groups.Tuple.tuple(MERCHANT_PENDING_FEE, 500L),
            org.assertj.core.groups.Tuple.tuple(PLATFORM_PENDING_FEE, -500L));
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
          new Register(99_999L, MERCHANT, RegisterTypes.PENDING_FEE.getValue());

      assertThatThrownBy(() -> repository.insertJournalEntry(pendingFeeEntry(missingRegister)))
          .isInstanceOf(DataAccessException.class);

      assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM journal_entry", Long.class))
          .isZero();
    } finally {
      jdbcTemplate.execute(
          "ALTER TABLE transaction_event ENABLE TRIGGER transaction_event_entry_coupling");
    }
  }

  @Test
  void findsThePendingFeeThePaymentsFeePendingEntryHolds() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              insertPaymentWithOrderCreatedEvent();
              repository.insertJournalEntry(pendingFeeEntry(MERCHANT_PENDING_FEE));
            });

    assertThat(repository.findPendingFeeByPayment(payment()))
        .contains(new PendingFee(eur(500L), MERCHANT_PENDING_FEE, PLATFORM_PENDING_FEE));
  }

  @Test
  void findsTheRegistersThePaymentsCaptureEntryPostedTo() {
    seedCaptureReferenceData();
    Transaction payment = payment();
    assertThat(repository.findCaptureRegistersByPayment(payment)).isEmpty();

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              insertPaymentWithOrderCreatedEvent();
              repository.insertJournalEntry(pendingFeeEntry(MERCHANT_PENDING_FEE));
              insertCaptureWithCapturedEvent();
              repository.insertJournalEntry(captureEntry(payment));
            });

    assertThat(repository.findCaptureRegistersByPayment(payment)).contains(CAPTURE_REGISTERS);
  }

  private static Transaction payment() {
    return Transaction.of(
        PAYMENT_ID, TransactionTypes.PAYMENT.getValue(), MERCHANT, "payment-1", eur(12_000L), NOW);
  }

  private static Amount eur(long quantity) {
    return new Amount(Currencies.EUR.getValue(), quantity);
  }

  private static JournalEntry pendingFeeEntry(Register merchantRegister) {
    TransactionEvent orderCreated =
        new TransactionEvent(
            ORDER_CREATED_EVENT_ID, payment(), TransactionEventTypes.ORDER_CREATED.getValue(), NOW);
    return PendingFeeJournalTemplates.FEE_PENDING.build(
        orderCreated, merchantRegister, PLATFORM_PENDING_FEE, eur(500L), NOW);
  }

  private static JournalEntry captureEntry(Transaction payment) {
    Transaction capture =
        Transaction.childOf(
            payment,
            CAPTURE_ID,
            TransactionTypes.CAPTURE.getValue(),
            MERCHANT,
            "capture-1",
            eur(12_000L),
            NOW);
    TransactionEvent captured =
        new TransactionEvent(
            CAPTURED_EVENT_ID, capture, TransactionEventTypes.CAPTURED.getValue(), NOW);
    return CaptureJournalTemplates.CAPTURE.build(
        captured,
        new PaymentDetail(
            payment, Countries.GERMANY.getValue(), null, PSP, eur(10_000L), eur(2_000L)),
        CAPTURE_REGISTERS,
        new PendingFee(eur(500L), MERCHANT_PENDING_FEE, PLATFORM_PENDING_FEE),
        NOW);
  }

  private void seedAccountsAndRegisters() {
    for (AccountTypes type : new AccountTypes[] {AccountTypes.PLATFORM, AccountTypes.MERCHANT}) {
      insertAccountType(type);
    }
    insertRegisterType(RegisterTypes.PENDING_FEE);
    jdbcTemplate.update(
        "INSERT INTO currency (currency_id, currency_code, exponent) VALUES (?, ?, ?)",
        Currencies.EUR.getValue().getCurrencyId(),
        Currencies.EUR.getValue().getCurrencyCode(),
        Currencies.EUR.getValue().getExponent());
    insertTransactionType(TransactionTypes.PAYMENT);
    insertTransactionEventType(TransactionEventTypes.ORDER_CREATED);
    insertJournalEntryType(JournalEntryTypes.FEE_PENDING);
    for (Account account : new Account[] {PLATFORM, MERCHANT}) {
      insertAccount(account);
    }
    for (Register register : new Register[] {PLATFORM_PENDING_FEE, MERCHANT_PENDING_FEE}) {
      insertRegister(register);
    }
  }

  private void seedCaptureReferenceData() {
    insertAccountType(AccountTypes.PSP);
    insertAccountType(AccountTypes.TAX_AUTHORITY);
    for (RegisterTypes type :
        new RegisterTypes[] {
          RegisterTypes.PSP_RECEIVABLE,
          RegisterTypes.TAX_PAYABLE,
          RegisterTypes.MERCHANT_PAYABLE,
          RegisterTypes.FEE_REVENUE
        }) {
      insertRegisterType(type);
    }
    insertTransactionType(TransactionTypes.CAPTURE);
    insertTransactionEventType(TransactionEventTypes.CAPTURED);
    insertJournalEntryType(JournalEntryTypes.CAPTURE);
    insertAccount(PSP);
    insertAccount(TAX_AUTHORITY);
    insertRegister(CAPTURE_REGISTERS.pspReceivableRegister());
    insertRegister(CAPTURE_REGISTERS.taxPayableRegister());
    insertRegister(CAPTURE_REGISTERS.merchantPayableRegister());
    insertRegister(CAPTURE_REGISTERS.feeRevenueRegister());
  }

  private void insertAccountType(AccountTypes type) {
    jdbcTemplate.update(
        "INSERT INTO account_type (account_type_id, code) VALUES (?, ?)",
        type.getValue().getAccountTypeId(),
        type.getValue().getCode());
  }

  private void insertRegisterType(RegisterTypes type) {
    jdbcTemplate.update(
        "INSERT INTO register_type (register_type_id, register_type_code) VALUES (?, ?)",
        type.getValue().getRegisterTypeId(),
        type.getValue().getCode());
  }

  private void insertTransactionType(TransactionTypes type) {
    jdbcTemplate.update(
        "INSERT INTO transaction_type (transaction_type_id, code) VALUES (?, ?)",
        type.getValue().getTransactionTypeId(),
        type.getValue().getCode());
  }

  private void insertTransactionEventType(TransactionEventTypes type) {
    jdbcTemplate.update(
        "INSERT INTO transaction_event_type "
            + "(transaction_event_type_id, code, requires_journal_entry) VALUES (?, ?, true)",
        type.getValue().getTransactionEventTypeId(),
        type.getValue().getCode());
  }

  private void insertJournalEntryType(JournalEntryTypes type) {
    jdbcTemplate.update(
        "INSERT INTO journal_entry_type (journal_entry_type_id, code) VALUES (?, ?)",
        type.getValue().getJournalEntryTypeId(),
        type.getValue().getCode());
  }

  private void insertAccount(Account account) {
    jdbcTemplate.update(
        "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
            + "VALUES (?, ?, ?, ?, true, ?)",
        account.getAccountId(),
        account.getAccountType().getAccountTypeId(),
        account.getCode(),
        account.getName(),
        Timestamp.from(NOW));
  }

  private void insertRegister(Register register) {
    jdbcTemplate.update(
        "INSERT INTO register (register_id, account_id, register_type_id) VALUES (?, ?, ?)",
        register.getRegisterId(),
        register.getAccount().getAccountId(),
        register.getRegisterType().getRegisterTypeId());
  }

  private void insertPaymentWithOrderCreatedEvent() {
    jdbcTemplate.update(
        "INSERT INTO transaction (transaction_id, transaction_type_id, account_id, reference, "
            + "quantity, currency_id, created_ts) VALUES (?, ?, ?, 'payment-1', 12000, ?, ?)",
        PAYMENT_ID,
        TransactionTypes.PAYMENT.getValue().getTransactionTypeId(),
        MERCHANT.getAccountId(),
        Currencies.EUR.getValue().getCurrencyId(),
        Timestamp.from(NOW));
    insertEvent(ORDER_CREATED_EVENT_ID, PAYMENT_ID, TransactionEventTypes.ORDER_CREATED);
  }

  private void insertCaptureWithCapturedEvent() {
    jdbcTemplate.update(
        "INSERT INTO transaction (transaction_id, transaction_type_id, parent_transaction_id, "
            + "account_id, reference, quantity, currency_id, created_ts) "
            + "VALUES (?, ?, ?, ?, 'capture-1', 12000, ?, ?)",
        CAPTURE_ID,
        TransactionTypes.CAPTURE.getValue().getTransactionTypeId(),
        PAYMENT_ID,
        MERCHANT.getAccountId(),
        Currencies.EUR.getValue().getCurrencyId(),
        Timestamp.from(NOW));
    insertEvent(CAPTURED_EVENT_ID, CAPTURE_ID, TransactionEventTypes.CAPTURED);
  }

  private void insertEvent(long eventId, long transactionId, TransactionEventTypes type) {
    jdbcTemplate.update(
        "INSERT INTO transaction_event (transaction_event_id, transaction_id, "
            + "transaction_event_type_id, event_ts) VALUES (?, ?, ?, ?)",
        eventId,
        transactionId,
        type.getValue().getTransactionEventTypeId(),
        Timestamp.from(NOW));
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EnableOutpostPersistence
  static class TestApplication {
    @Bean
    JournalEntryRepository journalEntryRepository(
        SqlSessionTemplate sqlSessionTemplate, PlatformTransactionManager transactionManager) {
      return new MyBatisJournalEntryRepository(
          sqlSessionTemplate, transactionManager, new SeededAccounts());
    }
  }

  /** The accounts the test seeds, found by id. */
  private static final class SeededAccounts implements AccountRepository {
    @Override
    public Optional<Account> findAccountById(long accountId) {
      return Stream.of(PLATFORM, MERCHANT, PSP, TAX_AUTHORITY)
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
