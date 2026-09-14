package com.outpost.accounting.control;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.account.AccountTypes;
import com.outpost.accounting.JournalEntryTypes;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionTypes;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Currencies;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.assertj.core.groups.Tuple;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Runs each journal integrity control against a captured and fully refunded payment booked from the
 * worked example in {@code MOR_SYSTEM_REQUIREMENTS_V3.md} §10.2: net 8000, tax 1680, gross 9680,
 * fee 400, in EUR.
 *
 * <p>The payment is committed once, so the database's own coupling and balance triggers accept it.
 * Each corruption is written in a transaction that bypasses those triggers and is rolled back.
 */
class JournalIntegrityControlsIntegrationTest {
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_journal_controls", "outpost_journal_controls", "outpost_journal_controls");

  private static final long PLATFORM = 100L;
  private static final long OTHER_PLATFORM = 150L;
  private static final long MERCHANT = 200L;
  private static final long OTHER_MERCHANT = 250L;
  private static final long PSP = 300L;
  private static final long TAX_AUTHORITY_NL = 400L;

  private static final long MERCHANT_PAYABLE_REGISTER = 2001L;
  private static final long MERCHANT_PENDING_FEE_REGISTER = 2008L;
  private static final long OTHER_MERCHANT_PENDING_FEE_REGISTER = 2508L;
  private static final long PLATFORM_FEE_REVENUE_REGISTER = 1005L;
  private static final long OTHER_PLATFORM_FEE_REVENUE_REGISTER = 1505L;
  private static final long PLATFORM_PENDING_FEE_REGISTER = 1008L;
  private static final long PSP_RECEIVABLE_REGISTER = 3003L;
  private static final long TAX_PAYABLE_REGISTER = 4004L;

  private static final long PAYMENT_ID = 10L;
  private static final long FEE_PENDING_ENTRY = 12L;
  private static final long CAPTURE_ID = 20L;
  private static final long CAPTURED_EVENT = 21L;
  private static final long CAPTURE_ENTRY = 22L;
  private static final long REFUND_ID = 30L;
  private static final long REFUND_REQUESTED_EVENT = 31L;
  private static final long REFUND_ENTRY = 33L;

  private static @Nullable HikariDataSource dataSource;
  private static @Nullable JdbcTemplate jdbc;
  private static @Nullable TransactionTemplate transactions;

  @BeforeAll
  static void bookCapturedAndRefundedPayment() {
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
        .locations("filesystem:" + requiredProperty("outpost.migration.location"))
        .load()
        .migrate();
    jdbc = new JdbcTemplate(database);
    transactions = new TransactionTemplate(new DataSourceTransactionManager(database));
    transactions().executeWithoutResult(status -> bookPayment(jdbc()));
  }

  @AfterAll
  static void closeDatabase() {
    if (dataSource != null) {
      dataSource.close();
    }
    DATABASE.stop();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "money_event_journal_entry_count",
        "journal_entry_template_mismatch",
        "transaction_detail_amount_mismatch",
        "unbalanced_currency_control_total"
      })
  void findsNothingInCorrectBooksWhenRunReadOnly(String control) {
    List<Map<String, Object>> findings =
        transactions()
            .execute(
                status -> {
                  jdbc().execute("SET TRANSACTION READ ONLY");
                  return jdbc().queryForList(control(control));
                });

    assertThat(findings).isEmpty();
  }

  @Test
  void controlTotalsMatchTheWorkedExampleAfterCaptureAndFullRefund() {
    List<Map<String, Object>> totals = jdbc().queryForList(control("totals/control_totals"));

    // After a full refund the merchant owes the retained 400 fee; everything else nets to zero.
    assertThat(totals)
        .extracting(
            row -> row.get("account_code"),
            row -> row.get("register_type"),
            row -> row.get("currency_code"),
            row -> ((Number) Objects.requireNonNull(row.get("total_quantity"))).longValue())
        .containsExactlyInAnyOrder(
            Tuple.tuple("OUTPOST", "FEE_REVENUE", "EUR", -400L),
            Tuple.tuple("OUTPOST", "PENDING_FEE", "EUR", 0L),
            Tuple.tuple("SHOP", "MERCHANT_PAYABLE", "EUR", 400L),
            Tuple.tuple("SHOP", "PENDING_FEE", "EUR", 0L),
            Tuple.tuple("PSP", "PSP_RECEIVABLE", "EUR", 0L),
            Tuple.tuple("TAX_NL", "TAX_PAYABLE", "EUR", 0L));
  }

  @Test
  void reportsMoneyEventWithoutJournalEntry() {
    List<Map<String, Object>> findings =
        findingsAfterCorruption(
            "money_event_journal_entry_count",
            transactionSql(40L, TransactionTypes.CAPTURE, PAYMENT_ID, "capture-2", 9680L),
            eventSql(41L, 40L, TransactionEventTypes.CAPTURED));

    assertThat(findings)
        .extracting(
            row -> row.get("transaction_reference"),
            JournalIntegrityControlsIntegrationTest::entryCount)
        .containsExactly(Tuple.tuple("capture-2", 0L));
  }

  @Test
  void reportsMoneyEventWithTwoJournalEntries() {
    List<Map<String, Object>> findings =
        findingsAfterCorruption(
            "money_event_journal_entry_count",
            "ALTER TABLE journal_entry DROP CONSTRAINT journal_entry_transaction_event_id_key",
            entrySql(50L, CAPTURED_EVENT, JournalEntryTypes.CAPTURE));

    assertThat(findings)
        .extracting(
            row -> row.get("transaction_reference"),
            JournalIntegrityControlsIntegrationTest::entryCount)
        .containsExactly(Tuple.tuple("capture-1", 2L));
  }

  @Test
  void reportsNonMoneyEventWithJournalEntry() {
    List<Map<String, Object>> findings =
        findingsAfterCorruption(
            "money_event_journal_entry_count",
            entrySql(51L, REFUND_REQUESTED_EVENT, JournalEntryTypes.REFUND));

    assertThat(findings)
        .extracting(
            row -> row.get("transaction_reference"),
            JournalIntegrityControlsIntegrationTest::entryCount)
        .containsExactly(Tuple.tuple("refund-1", 1L));
  }

  static Stream<Arguments> templateCorruptions() {
    return Stream.of(
        Arguments.of(
            "SOURCE",
            CAPTURE_ENTRY,
            "capture-1",
            new String[] {
              "UPDATE journal_entry SET journal_entry_type_id = "
                  + JournalEntryTypes.FEE_RELEASE.getValue().getJournalEntryTypeId()
                  + " WHERE journal_entry_id = "
                  + CAPTURE_ENTRY
            }),
        Arguments.of(
            "CARDINALITY",
            FEE_PENDING_ENTRY,
            "payment-1",
            new String[] {
              "DELETE FROM journal_entry_line WHERE journal_entry_id = "
                  + FEE_PENDING_ENTRY
                  + " AND register_id = "
                  + PLATFORM_PENDING_FEE_REGISTER
            }),
        Arguments.of(
            "ROLE",
            FEE_PENDING_ENTRY,
            "payment-1",
            new String[] {
              "UPDATE journal_entry_line SET register_id = "
                  + OTHER_MERCHANT_PENDING_FEE_REGISTER
                  + " WHERE register_id = "
                  + MERCHANT_PENDING_FEE_REGISTER
                  + " AND journal_entry_id = "
                  + FEE_PENDING_ENTRY
            }),
        Arguments.of(
            "ROLE",
            CAPTURE_ENTRY,
            "capture-1",
            new String[] {
              "UPDATE journal_entry_line SET register_id = "
                  + OTHER_PLATFORM_FEE_REVENUE_REGISTER
                  + " WHERE register_id = "
                  + PLATFORM_FEE_REVENUE_REGISTER
                  + " AND journal_entry_id = "
                  + CAPTURE_ENTRY
            }),
        Arguments.of(
            "SIGN",
            CAPTURE_ENTRY,
            "capture-1",
            new String[] {
              lineQuantitySql(CAPTURE_ENTRY, PLATFORM_FEE_REVENUE_REGISTER, 400L),
              lineQuantitySql(CAPTURE_ENTRY, MERCHANT_PAYABLE_REGISTER, -8400L)
            }),
        Arguments.of(
            "CURRENCY",
            REFUND_ENTRY,
            "refund-1",
            new String[] {
              "UPDATE journal_entry_line SET currency_id = "
                  + Currencies.USD.getValue().getCurrencyId()
                  + " WHERE journal_entry_id = "
                  + REFUND_ENTRY
            }),
        Arguments.of(
            "FORMULA",
            CAPTURE_ENTRY,
            "capture-1",
            new String[] {
              lineQuantitySql(CAPTURE_ENTRY, TAX_PAYABLE_REGISTER, -1580L),
              lineQuantitySql(CAPTURE_ENTRY, MERCHANT_PAYABLE_REGISTER, -7700L)
            }));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("templateCorruptions")
  void reportsJournalEntryThatDisagreesWithItsTemplate(
      String mismatch, long journalEntryId, String transactionReference, String[] corruption) {
    List<Map<String, Object>> findings =
        findingsAfterCorruption("journal_entry_template_mismatch", corruption);

    assertThat(findings)
        .extracting(
            row -> row.get("mismatch"),
            row -> ((Number) Objects.requireNonNull(row.get("journal_entry_id"))).longValue(),
            row -> row.get("transaction_reference"))
        .contains(Tuple.tuple(mismatch, journalEntryId, transactionReference));
  }

  static Stream<Arguments> detailCorruptions() {
    return Stream.of(
        Arguments.of(
            "GROSS_NOT_NET_PLUS_TAX",
            "payment-1",
            "UPDATE payment_detail SET tax_quantity = 1600 WHERE transaction_id = " + PAYMENT_ID),
        Arguments.of(
            "NEGATIVE_AMOUNT",
            "payment-1",
            "UPDATE payment_detail SET net_quantity = -1, tax_quantity = 9681 "
                + "WHERE transaction_id = "
                + PAYMENT_ID),
        Arguments.of(
            "MISSING_DETAIL",
            "refund-1",
            "DELETE FROM refund_detail WHERE transaction_id = " + REFUND_ID));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("detailCorruptions")
  void reportsTransactionWhoseDetailDoesNotAddUp(
      String mismatch, String transactionReference, String corruption) {
    List<Map<String, Object>> findings =
        findingsAfterCorruption("transaction_detail_amount_mismatch", corruption);

    assertThat(findings)
        .extracting(row -> row.get("mismatch"), row -> row.get("transaction_reference"))
        .containsExactly(Tuple.tuple(mismatch, transactionReference));
  }

  @Test
  void reportsCurrencyWhoseLinesDoNotSumToZero() {
    List<Map<String, Object>> findings =
        findingsAfterCorruption(
            "unbalanced_currency_control_total",
            lineQuantitySql(CAPTURE_ENTRY, PSP_RECEIVABLE_REGISTER, 9000L));

    assertThat(findings)
        .extracting(
            row -> row.get("currency_code"),
            row -> ((Number) Objects.requireNonNull(row.get("total_quantity"))).longValue())
        .containsExactly(Tuple.tuple("EUR", -680L));
  }

  private static List<Map<String, Object>> findingsAfterCorruption(
      String control, String... corruption) {
    return Objects.requireNonNull(
        transactions()
            .execute(
                status -> {
                  status.setRollbackOnly();
                  // Replica mode skips the append-only, coupling, and balance triggers for this
                  // transaction only, so history the database normally refuses can be written.
                  jdbc().execute("SET LOCAL session_replication_role = replica");
                  for (String statement : corruption) {
                    jdbc().execute(statement);
                  }
                  return jdbc().queryForList(control(control));
                }));
  }

  private static void bookPayment(JdbcTemplate jdbc) {
    for (AccountTypes type : AccountTypes.values()) {
      jdbc.update(
          "INSERT INTO account_type (account_type_id, code) VALUES (?, ?)",
          type.getValue().getAccountTypeId(),
          type.getValue().getCode());
    }
    for (RegisterTypes type : RegisterTypes.values()) {
      jdbc.update(
          "INSERT INTO register_type (register_type_id, register_type_code) VALUES (?, ?)",
          type.getValue().getRegisterTypeId(),
          type.getValue().getCode());
    }
    for (Currencies currency : List.of(Currencies.EUR, Currencies.USD)) {
      jdbc.update(
          "INSERT INTO currency (currency_id, currency_code, exponent) VALUES (?, ?, ?)",
          currency.getValue().getCurrencyId(),
          currency.getValue().getCurrencyCode(),
          currency.getValue().getExponent());
    }
    jdbc.update(
        "INSERT INTO country (country_id, iso_code, name) VALUES (?, ?, ?)",
        Countries.NETHERLANDS.getValue().getCountryId(),
        Countries.NETHERLANDS.getValue().getIsoCode(),
        Countries.NETHERLANDS.getValue().getName());
    for (TransactionTypes type : TransactionTypes.values()) {
      jdbc.update(
          "INSERT INTO transaction_type (transaction_type_id, code) VALUES (?, ?)",
          type.getValue().getTransactionTypeId(),
          type.getValue().getCode());
    }
    for (TransactionEventTypes type : TransactionEventTypes.values()) {
      jdbc.update(
          "INSERT INTO transaction_event_type "
              + "(transaction_event_type_id, code, requires_journal_entry) VALUES (?, ?, ?)",
          type.getValue().getTransactionEventTypeId(),
          type.getValue().getCode(),
          type.getValue().requiresJournalEntry());
    }
    for (JournalEntryTypes type : JournalEntryTypes.values()) {
      jdbc.update(
          "INSERT INTO journal_entry_type (journal_entry_type_id, code) VALUES (?, ?)",
          type.getValue().getJournalEntryTypeId(),
          type.getValue().getCode());
    }

    account(jdbc, PLATFORM, AccountTypes.PLATFORM, "OUTPOST");
    account(jdbc, OTHER_PLATFORM, AccountTypes.PLATFORM, "OTHER_PLATFORM");
    account(jdbc, MERCHANT, AccountTypes.MERCHANT, "SHOP");
    account(jdbc, OTHER_MERCHANT, AccountTypes.MERCHANT, "OTHER_SHOP");
    account(jdbc, PSP, AccountTypes.PSP, "PSP");
    account(jdbc, TAX_AUTHORITY_NL, AccountTypes.TAX_AUTHORITY, "TAX_NL");
    jdbc.update(
        "INSERT INTO tax_authority_account (country_id, account_id, account_type_id) "
            + "VALUES (?, ?, ?)",
        Countries.NETHERLANDS.getValue().getCountryId(),
        TAX_AUTHORITY_NL,
        AccountTypes.TAX_AUTHORITY.getValue().getAccountTypeId());
    register(jdbc, MERCHANT_PAYABLE_REGISTER, MERCHANT, RegisterTypes.MERCHANT_PAYABLE);
    register(jdbc, MERCHANT_PENDING_FEE_REGISTER, MERCHANT, RegisterTypes.PENDING_FEE);
    register(jdbc, OTHER_MERCHANT_PENDING_FEE_REGISTER, OTHER_MERCHANT, RegisterTypes.PENDING_FEE);
    register(jdbc, PLATFORM_FEE_REVENUE_REGISTER, PLATFORM, RegisterTypes.FEE_REVENUE);
    register(jdbc, OTHER_PLATFORM_FEE_REVENUE_REGISTER, OTHER_PLATFORM, RegisterTypes.FEE_REVENUE);
    register(jdbc, PLATFORM_PENDING_FEE_REGISTER, PLATFORM, RegisterTypes.PENDING_FEE);
    register(jdbc, PSP_RECEIVABLE_REGISTER, PSP, RegisterTypes.PSP_RECEIVABLE);
    register(jdbc, TAX_PAYABLE_REGISTER, TAX_AUTHORITY_NL, RegisterTypes.TAX_PAYABLE);

    jdbc.execute(transactionSql(PAYMENT_ID, TransactionTypes.PAYMENT, null, "payment-1", 9680L));
    jdbc.update(
        "INSERT INTO payment_detail (transaction_id, transaction_type_id, shopper_country_id, "
            + "psp_account_id, net_quantity, tax_quantity) VALUES (?, ?, ?, ?, 8000, 1680)",
        PAYMENT_ID,
        TransactionTypes.PAYMENT.getValue().getTransactionTypeId(),
        Countries.NETHERLANDS.getValue().getCountryId(),
        PSP);
    jdbc.execute(eventSql(11L, PAYMENT_ID, TransactionEventTypes.ORDER_CREATED));
    jdbc.execute(entrySql(FEE_PENDING_ENTRY, 11L, JournalEntryTypes.FEE_PENDING));
    line(jdbc, FEE_PENDING_ENTRY, MERCHANT_PENDING_FEE_REGISTER, 400L);
    line(jdbc, FEE_PENDING_ENTRY, PLATFORM_PENDING_FEE_REGISTER, -400L);

    jdbc.execute(
        transactionSql(CAPTURE_ID, TransactionTypes.CAPTURE, PAYMENT_ID, "capture-1", 9680L));
    jdbc.execute(eventSql(CAPTURED_EVENT, CAPTURE_ID, TransactionEventTypes.CAPTURED));
    jdbc.execute(entrySql(CAPTURE_ENTRY, CAPTURED_EVENT, JournalEntryTypes.CAPTURE));
    line(jdbc, CAPTURE_ENTRY, PSP_RECEIVABLE_REGISTER, 9680L);
    line(jdbc, CAPTURE_ENTRY, TAX_PAYABLE_REGISTER, -1680L);
    line(jdbc, CAPTURE_ENTRY, MERCHANT_PAYABLE_REGISTER, -7600L);
    line(jdbc, CAPTURE_ENTRY, PLATFORM_FEE_REVENUE_REGISTER, -400L);
    line(jdbc, CAPTURE_ENTRY, MERCHANT_PENDING_FEE_REGISTER, -400L);
    line(jdbc, CAPTURE_ENTRY, PLATFORM_PENDING_FEE_REGISTER, 400L);

    jdbc.execute(transactionSql(REFUND_ID, TransactionTypes.REFUND, PAYMENT_ID, "refund-1", 9680L));
    jdbc.update(
        "INSERT INTO refund_detail (transaction_id, transaction_type_id, net_quantity, "
            + "tax_quantity) VALUES (?, ?, 8000, 1680)",
        REFUND_ID,
        TransactionTypes.REFUND.getValue().getTransactionTypeId());
    jdbc.execute(
        eventSql(REFUND_REQUESTED_EVENT, REFUND_ID, TransactionEventTypes.REFUND_REQUESTED));
    jdbc.execute(eventSql(32L, REFUND_ID, TransactionEventTypes.REFUNDED));
    jdbc.execute(entrySql(REFUND_ENTRY, 32L, JournalEntryTypes.REFUND));
    line(jdbc, REFUND_ENTRY, PSP_RECEIVABLE_REGISTER, -9680L);
    line(jdbc, REFUND_ENTRY, TAX_PAYABLE_REGISTER, 1680L);
    line(jdbc, REFUND_ENTRY, MERCHANT_PAYABLE_REGISTER, 8000L);
  }

  private static void account(JdbcTemplate jdbc, long accountId, AccountTypes type, String code) {
    jdbc.update(
        "INSERT INTO account (account_id, account_type_id, code, name, is_active, created_ts) "
            + "VALUES (?, ?, ?, ?, true, now())",
        accountId,
        type.getValue().getAccountTypeId(),
        code,
        code);
  }

  private static void register(
      JdbcTemplate jdbc, long registerId, long accountId, RegisterTypes type) {
    jdbc.update(
        "INSERT INTO register (register_id, account_id, register_type_id) VALUES (?, ?, ?)",
        registerId,
        accountId,
        type.getValue().getRegisterTypeId());
  }

  private static void line(JdbcTemplate jdbc, long journalEntryId, long registerId, long quantity) {
    jdbc.update(
        "INSERT INTO journal_entry_line (journal_entry_id, register_id, currency_id, quantity) "
            + "VALUES (?, ?, ?, ?)",
        journalEntryId,
        registerId,
        Currencies.EUR.getValue().getCurrencyId(),
        quantity);
  }

  private static String transactionSql(
      long transactionId,
      TransactionTypes type,
      @Nullable Long parentTransactionId,
      String reference,
      long quantity) {
    return "INSERT INTO transaction (transaction_id, transaction_type_id, parent_transaction_id, "
        + "account_id, reference, quantity, currency_id, created_ts) VALUES ("
        + transactionId
        + ", "
        + type.getValue().getTransactionTypeId()
        + ", "
        + parentTransactionId
        + ", "
        + MERCHANT
        + ", '"
        + reference
        + "', "
        + quantity
        + ", "
        + Currencies.EUR.getValue().getCurrencyId()
        + ", now())";
  }

  private static String eventSql(long eventId, long transactionId, TransactionEventTypes type) {
    return "INSERT INTO transaction_event (transaction_event_id, transaction_id, "
        + "transaction_event_type_id, event_ts) VALUES ("
        + eventId
        + ", "
        + transactionId
        + ", "
        + type.getValue().getTransactionEventTypeId()
        + ", now())";
  }

  private static String entrySql(long entryId, long eventId, JournalEntryTypes type) {
    return "INSERT INTO journal_entry (journal_entry_id, transaction_event_id, "
        + "journal_entry_type_id, booked, posted) VALUES ("
        + entryId
        + ", "
        + eventId
        + ", "
        + type.getValue().getJournalEntryTypeId()
        + ", now(), now())";
  }

  private static String lineQuantitySql(long journalEntryId, long registerId, long quantity) {
    return "UPDATE journal_entry_line SET quantity = "
        + quantity
        + " WHERE journal_entry_id = "
        + journalEntryId
        + " AND register_id = "
        + registerId;
  }

  private static long entryCount(Map<String, Object> row) {
    return ((Number) Objects.requireNonNull(row.get("journal_entry_count"))).longValue();
  }

  private static String control(String name) {
    try {
      return Files.readString(Path.of(requiredProperty("outpost.control.location"), name + ".sql"));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String requiredProperty(String name) {
    String value = System.getProperty(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " is required");
    }
    return value;
  }

  private static JdbcTemplate jdbc() {
    return Objects.requireNonNull(jdbc);
  }

  private static TransactionTemplate transactions() {
    return Objects.requireNonNull(transactions);
  }
}
