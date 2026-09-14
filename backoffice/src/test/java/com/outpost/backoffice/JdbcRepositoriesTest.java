package com.outpost.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.outpost.backoffice.merchant.MerchantRepository;
import com.outpost.backoffice.payment.Payment;
import com.outpost.backoffice.payment.PaymentEvent;
import com.outpost.backoffice.payment.PaymentJournalLine;
import com.outpost.backoffice.payment.PaymentRepository;
import com.outpost.backoffice.register.RegisterBalance;
import com.outpost.backoffice.register.RegisterBalanceRepository;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The payments and the register balances come from Outpost's tables, read through the back office's
 * own queries over the schema Outpost's migrations create.
 */
@SpringBootTest(
    classes = BackOfficeApplication.class,
    properties = {
      "backoffice.operator-api-key=operator",
      "backoffice.psp-api-key=psp",
      "backoffice.merchants.DEMO_MERCHANT.api-key=key",
      "backoffice.merchants.DEMO_MERCHANT.hmac-secret=secret",
      "backoffice.catalogue[0].sku=EBOOK",
      "backoffice.catalogue[0].name=E-book",
      "backoffice.catalogue[0].amount=1900",
      "backoffice.catalogue[0].currency=EUR",
      "backoffice.catalogue[0].type=DIGITAL_GOODS"
    })
class JdbcRepositoriesTest {
  private static final PostgreSQLContainer<?> DATABASE =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:18"))
          .withDatabaseName("back_office_test")
          .withUsername("back_office_test")
          .withPassword("back_office_test");

  @Autowired private MerchantRepository merchants;
  @Autowired private PaymentRepository payments;
  @Autowired private RegisterBalanceRepository balances;

  @BeforeAll
  static void migrateAndSeed() {
    DATABASE.start();
    Flyway.configure()
        .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
        .locations("filesystem:" + System.getProperty("outpost.migration.location"))
        .load()
        .migrate();
    DataSource dataSource =
        DataSourceBuilder.create()
            .url(DATABASE.getJdbcUrl())
            .username(DATABASE.getUsername())
            .password(DATABASE.getPassword())
            .build();
    // The journal's balance and coupling constraints are checked at commit, so the seed is one
    // transaction.
    new TransactionTemplate(new DataSourceTransactionManager(dataSource))
        .executeWithoutResult(status -> seed(new JdbcTemplate(dataSource)));
  }

  private static void seed(JdbcTemplate seed) {
    seed.update(
        "INSERT INTO account_type (account_type_id, code) VALUES (1, 'ROOT'), (2, 'MERCHANT'), "
            + "(4, 'PSP'), (6, 'PLATFORM')");
    seed.update(
        "INSERT INTO account (account_id, account_type_id, parent_account_id, code, name, "
            + "is_active, created_ts) VALUES "
            + "(1, 1, NULL, 'ROOT', 'Root', true, now()), "
            + "(100, 6, 1, 'OUTPOST', 'Outpost', true, now()), "
            + "(200, 2, 1, 'DEMO_MERCHANT', 'Demo Merchant', true, now()), "
            + "(300, 4, 1, 'DEMO_PSP', 'Demo PSP', true, now())");
    seed.update("INSERT INTO merchant_psp (account_id, psp_account_id) VALUES (200, 300)");
    seed.update(
        "INSERT INTO register_type (register_type_id, register_type_code) VALUES "
            + "(1, 'MERCHANT_PAYABLE'), (5, 'FEE_REVENUE'), (8, 'PENDING_FEE')");
    seed.update(
        "INSERT INTO register (register_id, account_id, register_type_id) VALUES "
            + "(20001, 200, 1), (20008, 200, 8), (10005, 100, 5), (10008, 100, 8)");
    seed.update(
        "INSERT INTO currency (currency_id, currency_code, exponent) VALUES (3, 'EUR', 2), "
            + "(4, 'USD', 2)");
    seed.update("INSERT INTO fee_mode (fee_mode_id, code) VALUES (1, 'PERCENTAGE')");
    seed.update(
        "INSERT INTO merchant_fee_configuration (account_id, account_type_id, currency_id, "
            + "fee_mode_id, fee_rate_bps, fee_fixed) VALUES (200, 2, 3, 1, 500, NULL), "
            + "(200, 2, 4, 1, 500, NULL)");
    seed.update("INSERT INTO country (country_id, iso_code, name) VALUES (6, 'DE', 'Germany')");
    seed.update(
        "INSERT INTO product_type (product_type_id, code) VALUES (1, 'PHYSICAL_GOODS'), "
            + "(2, 'DIGITAL_GOODS')");
    long shopper =
        Objects.requireNonNull(
            seed.queryForObject(
                "INSERT INTO shopper_detail (email, full_name, country_id) "
                    + "VALUES ('s@example.test', 'Shopper', 6) RETURNING shopper_id",
                Long.class));
    seed.update(
        "INSERT INTO merchant_order (order_id, order_reference, merchant_reference, account_id, "
            + "account_type_id, shopper_id, currency_id, net_amount, tax_amount, gross_amount, "
            + "idempotency_key, request_fingerprint, psp_account_id, psp_reference, payment_link, "
            + "shopper_country_id, created_ts) VALUES "
            + "(1, 'order-paid', 'm-1', 200, 2, ?, 3, 100, 19, 119, 'k-1', 'f-1', 300, '41', "
            + "'http://psp/v1/DEMO_PSP/payment', 6, now() - interval '1 minute'), "
            + "(2, 'order-failed', 'm-2', 200, 2, ?, 3, 100, 19, 119, 'k-2', 'f-2', 300, NULL, "
            + "NULL, 6, now())",
        shopper,
        shopper);
    seed.update(
        "INSERT INTO order_item (order_item_id, order_id, product_type_id, order_line_reference, "
            + "merchant_line_reference, net_amount, tax_amount, tax_rate) VALUES "
            + "(1, 1, 1, 'line-1', 'TSHIRT', 60, 11, 0.19), "
            + "(2, 1, 2, 'line-2', 'EBOOK', 40, 8, 0.19)");
    seed.update("INSERT INTO transaction_type (transaction_type_id, code) VALUES (1, 'PAYMENT')");
    seed.update(
        "INSERT INTO transaction_event_type (transaction_event_type_id, code, "
            + "requires_journal_entry) VALUES (1, 'ORDER_CREATED', true), "
            + "(2, 'AUTHORISED', false)");
    seed.update(
        "INSERT INTO transaction (transaction_id, transaction_type_id, parent_transaction_id, "
            + "account_id, reference, quantity, currency_id, created_ts) VALUES "
            + "(1, 1, NULL, 200, 'order-paid', 119, 3, now())");
    seed.update(
        "INSERT INTO transaction_event (transaction_event_id, transaction_id, "
            + "transaction_event_type_id, event_ts) VALUES (1, 1, 1, now()), (2, 1, 2, now())");
    seed.update(
        "INSERT INTO journal_entry_type (journal_entry_type_id, code) VALUES (3, 'FEE_PENDING')");
    seed.update(
        "INSERT INTO journal_entry (journal_entry_id, transaction_event_id, journal_entry_type_id, "
            + "booked, posted) VALUES (1, 1, 3, now(), now())");
    seed.update(
        "INSERT INTO journal_entry_line (journal_entry_id, register_id, currency_id, quantity) "
            + "VALUES (1, 20008, 3, 5), (1, 10008, 3, -5)");
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
    registry.add("spring.datasource.username", DATABASE::getUsername);
    registry.add("spring.datasource.password", DATABASE::getPassword);
  }

  @Test
  void listsMerchantsPspsAndCountries() {
    assertThat(merchants.findActiveMerchants()).extracting("code").containsExactly("DEMO_MERCHANT");
    assertThat(merchants.findEnabledPsps("DEMO_MERCHANT"))
        .extracting("code")
        .containsExactly("DEMO_PSP");
    assertThat(merchants.findCountryCodes()).containsExactly("DE");
  }

  @Test
  void listsPaymentsNewestFirstWithWhatTheLedgerBookedForThem() {
    assertThat(payments.findPayments())
        .extracting(
            Payment::orderReference,
            Payment::pspReference,
            Payment::merchantName,
            Payment::pspName,
            Payment::lastEvent,
            Payment::platformFee,
            Payment::shopperCountry,
            Payment::goodsTypes)
        .containsExactly(
            tuple("order-failed", null, "Demo Merchant", "Demo PSP", null, null, "DE", List.of()),
            tuple(
                "order-paid",
                "41",
                "Demo Merchant",
                "Demo PSP",
                "AUTHORISED",
                5L,
                "DE",
                java.util.List.of("PHYSICAL_GOODS", "DIGITAL_GOODS")));
  }

  @Test
  void listsTheEventsAndJournalLinesBookedForOnePayment() {
    assertThat(payments.findPaymentEvents("order-paid"))
        .extracting(PaymentEvent::transactionType, PaymentEvent::eventType, PaymentEvent::quantity)
        .containsExactly(
            tuple("PAYMENT", "ORDER_CREATED", 119L), tuple("PAYMENT", "AUTHORISED", 119L));
    assertThat(payments.findPaymentJournalLines("order-paid"))
        .extracting(
            PaymentJournalLine::entryType,
            PaymentJournalLine::eventType,
            PaymentJournalLine::accountCode,
            PaymentJournalLine::registerType,
            PaymentJournalLine::currency,
            PaymentJournalLine::quantity)
        .containsExactly(
            tuple("FEE_PENDING", "ORDER_CREATED", "DEMO_MERCHANT", "PENDING_FEE", "EUR", 5L),
            tuple("FEE_PENDING", "ORDER_CREATED", "OUTPOST", "PENDING_FEE", "EUR", -5L));
    assertThat(payments.findPaymentEvents("order-failed")).isEmpty();
  }

  @Test
  void listsEveryRegisterInEveryOperatingCurrencyWithZeroWhereNothingIsBookedFilteredAsAsked() {
    assertThat(balances.findBalances(List.of(), List.of("DEMO_MERCHANT"), List.of()))
        .extracting(
            RegisterBalance::registerType,
            RegisterBalance::currency,
            RegisterBalance::debits,
            RegisterBalance::credits)
        .containsExactly(
            tuple("MERCHANT_PAYABLE", "EUR", 0L, 0L),
            tuple("MERCHANT_PAYABLE", "USD", 0L, 0L),
            tuple("PENDING_FEE", "EUR", 5L, 0L),
            tuple("PENDING_FEE", "USD", 0L, 0L));
    assertThat(balances.findBalances(List.of(), List.of(), List.of()))
        .extracting(
            RegisterBalance::accountType,
            RegisterBalance::accountCode,
            RegisterBalance::registerType,
            RegisterBalance::currency,
            RegisterBalance::credits)
        .contains(
            tuple("PLATFORM", "OUTPOST", "PENDING_FEE", "EUR", 5L),
            tuple("PLATFORM", "OUTPOST", "FEE_REVENUE", "USD", 0L))
        .hasSize(8);
    assertThat(balances.findBalances(List.of("PLATFORM", "PSP"), List.of(), List.of("PENDING_FEE")))
        .extracting(RegisterBalance::accountCode, RegisterBalance::currency)
        .containsExactly(tuple("OUTPOST", "EUR"), tuple("OUTPOST", "USD"));
    assertThat(balances.findAccountTypeCodes()).containsExactly("MERCHANT", "PLATFORM");
    assertThat(balances.findAccountCodes(List.of("MERCHANT"))).containsExactly("DEMO_MERCHANT");
    assertThat(balances.findRegisterTypeCodes())
        .containsExactly("FEE_REVENUE", "MERCHANT_PAYABLE", "PENDING_FEE");
  }
}
