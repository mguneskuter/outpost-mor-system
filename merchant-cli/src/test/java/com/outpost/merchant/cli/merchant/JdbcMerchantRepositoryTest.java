package com.outpost.merchant.cli.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.outpost.merchant.cli.MerchantCliApplication;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.shell.core.NonInteractiveShellRunner;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The merchant list, an order's payment facts, and what the Ledger booked for an order come from
 * Outpost's tables, read through the shell's own queries over the schema Outpost's migrations
 * create.
 */
@SpringBootTest(
    classes = MerchantCliApplication.class,
    properties = {
      "merchant.cli.operator-api-key=operator",
      "merchant.cli.psp-api-key=psp",
      "merchant.cli.merchants.DEMO_MERCHANT.api-key=key",
      "merchant.cli.merchants.DEMO_MERCHANT.hmac-secret=secret",
      "merchant.cli.catalogue[0].sku=EBOOK",
      "merchant.cli.catalogue[0].name=E-book",
      "merchant.cli.catalogue[0].amount=1900",
      "merchant.cli.catalogue[0].currency=EUR",
      "merchant.cli.catalogue[0].type=DIGITAL_GOODS"
    })
class JdbcMerchantRepositoryTest {
  private static final PostgreSQLContainer<?> DATABASE =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:18"))
          .withDatabaseName("merchant_cli_test")
          .withUsername("merchant_cli_test")
          .withPassword("merchant_cli_test");

  @Autowired private MerchantRepository merchants;
  @Autowired private CommandParser commandParser;
  @Autowired private CommandRegistry commandRegistry;

  @BeforeAll
  static void migrateAndSeed() {
    DATABASE.start();
    Flyway.configure()
        .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
        .locations("filesystem:" + System.getProperty("outpost.migration.location"))
        .load()
        .migrate();
    JdbcTemplate seed =
        new JdbcTemplate(
            DataSourceBuilder.create()
                .url(DATABASE.getJdbcUrl())
                .username(DATABASE.getUsername())
                .password(DATABASE.getPassword())
                .build());
    seed.update(
        "INSERT INTO account_type (account_type_id, code) VALUES (1, 'ROOT'), (2, 'MERCHANT'), "
            + "(4, 'PSP')");
    seed.update(
        "INSERT INTO account (account_id, account_type_id, parent_account_id, code, name, "
            + "is_active, created_ts) VALUES "
            + "(1, 1, NULL, 'ROOT', 'Root', true, now()), "
            + "(200, 2, 1, 'DEMO_MERCHANT', 'Demo Merchant', true, now()), "
            + "(210, 2, 1, 'CLOSED_MERCHANT', 'Closed Merchant', false, now()), "
            + "(220, 2, 1, 'ANOTHER_MERCHANT', 'Another Merchant', true, now()), "
            + "(300, 4, 1, 'DEMO_PSP', 'Demo PSP', true, now()), "
            + "(310, 4, 1, 'OTHER_PSP', 'Other PSP', true, now()), "
            + "(320, 4, 1, 'GONE_PSP', 'Gone PSP', false, now())");
    seed.update(
        "INSERT INTO merchant_psp (account_id, psp_account_id) VALUES (200, 300), (200, 320), "
            + "(220, 310)");
    seed.update("INSERT INTO country (country_id, iso_code, name) VALUES (6, 'DE', 'Germany')");
    seed.update("INSERT INTO currency (currency_id, currency_code, exponent) VALUES (3, 'EUR', 2)");
    long shopper =
        Objects.requireNonNull(
            seed.queryForObject(
                "INSERT INTO shopper_detail (email, full_name, country_id) "
                    + "VALUES ('s@example.test', 'Shopper', 6) RETURNING shopper_id",
                Long.class));
    seed.update(
        "INSERT INTO merchant_order (order_reference, merchant_reference, account_id, "
            + "account_type_id, shopper_id, currency_id, net_amount, tax_amount, gross_amount, "
            + "idempotency_key, request_fingerprint, psp_account_id, psp_reference, payment_link, "
            + "shopper_country_id, created_ts) VALUES "
            + "('order-paid', 'm-1', 200, 2, ?, 3, 100, 19, 119, 'k-1', 'f-1', 300, '41', "
            + "'http://psp/v1/DEMO_PSP/payment', 6, now()), "
            + "('order-unpaid', 'm-2', 200, 2, ?, 3, 100, 19, 119, 'k-2', 'f-2', 300, NULL, "
            + "NULL, 6, now())",
        shopper,
        shopper);
    seed.update(
        "INSERT INTO transaction_type (transaction_type_id, code) VALUES (1, 'PAYMENT'), "
            + "(2, 'CAPTURE')");
    seed.update(
        "INSERT INTO transaction_event_type (transaction_event_type_id, code, "
            + "requires_journal_entry) VALUES (1, 'ORDER_CREATED', false), "
            + "(2, 'AUTHORISED', false), (5, 'CAPTURED', false)");
    seed.update(
        "INSERT INTO transaction (transaction_id, transaction_type_id, parent_transaction_id, "
            + "account_id, reference, quantity, currency_id, created_ts) VALUES "
            + "(1, 1, NULL, 200, 'order-paid', 119, 3, now()), "
            + "(2, 2, 1, 200, 'capture-1', 119, 3, now()), "
            + "(3, 1, NULL, 200, 'order-other', 119, 3, now())");
    seed.update(
        "INSERT INTO transaction_event (transaction_event_id, transaction_id, "
            + "transaction_event_type_id, event_ts) VALUES (1, 1, 1, now()), (2, 1, 2, now()), "
            + "(3, 2, 5, now()), (4, 3, 1, now())");
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
    registry.add("spring.datasource.username", DATABASE::getUsername);
    registry.add("spring.datasource.password", DATABASE::getPassword);
  }

  @Test
  void listsOnlyActiveMerchantsOrderedByCode() {
    assertThat(merchants.findActiveMerchants())
        .containsExactly(
            new Merchant("ANOTHER_MERCHANT", "Another Merchant"),
            new Merchant("DEMO_MERCHANT", "Demo Merchant"));
  }

  @Test
  void listsTheActivePspsEnabledForMerchant() {
    assertThat(merchants.findEnabledPsps("DEMO_MERCHANT"))
        .containsExactly(new Psp("DEMO_PSP", "Demo PSP"));
    assertThat(merchants.findEnabledPsps("CLOSED_MERCHANT")).isEmpty();
  }

  @Test
  void theShellPrintsTheMerchantListOnRequest() throws Exception {
    StringWriter printed = new StringWriter();
    new NonInteractiveShellRunner(commandParser, commandRegistry, new PrintWriter(printed))
        .run(new String[] {"merchants"});

    assertThat(printed.toString())
        .contains("* DEMO_MERCHANT  Demo Merchant")
        .contains("  ANOTHER_MERCHANT  Another Merchant  (no credentials configured)");
  }

  @Test
  void listsTheEventsBookedOnAnOrdersPaymentAndItsChildrenOldestFirst() {
    assertThat(merchants.findOrderEvents("order-paid"))
        .extracting(OrderEvent::transactionType, OrderEvent::eventType)
        .containsExactly(
            tuple("PAYMENT", "ORDER_CREATED"),
            tuple("PAYMENT", "AUTHORISED"),
            tuple("CAPTURE", "CAPTURED"));
    assertThat(merchants.findOrderEvents("order-unpaid")).isEmpty();
  }

  @Test
  void findsThePaymentFactsOfAnOrderThePspAccepted() {
    assertThat(merchants.findOrderPayment("order-paid"))
        .contains(new OrderPayment("41", "http://psp/v1/DEMO_PSP/payment"));
    assertThat(merchants.findOrderPayment("order-unpaid")).isEmpty();
    assertThat(merchants.findOrderPayment("order-unknown")).isEmpty();
  }
}
