package com.outpost.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.outpost.account.AccountTypes;
import com.outpost.account.configuration.FeeModes;
import com.outpost.accounting.AccountTypeRegisterTypes;
import com.outpost.accounting.JournalEntryTypes;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionTypes;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.Currencies;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.hmac.HmacSha256;
import com.outpost.ledger.payment.api.SignatureFilter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = LedgerApiApplication.class)
class PaymentCreationIntegrationTest {

  private static final String SECRET = "test-gateway-secret";
  private static final HmacKey GATEWAY_KEY = HmacKey.fromUtf8(SECRET);
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer("outpost_payment_creation", "outpost", "outpost");
  private static final String VALID_BODY =
      "{\"payment_reference\":\"payment-1\",\"merchant_code\":\"DEMO_MERCHANT\","
          + "\"psp_code\":\"DEMO_PSP\",\"shopper_country\":\"US\","
          + "\"shopper_country_subdivision\":\"US-CA\",\"net_amount\":10000,"
          + "\"tax_amount\":2000,\"gross_amount\":12000,\"currency\":\"EUR\"}";

  @Autowired private WebApplicationContext applicationContext;
  @Autowired private SignatureFilter signatureFilter;
  @Autowired private JdbcTemplate jdbcTemplate;
  private MockMvc mockMvc;

  @BeforeEach
  void setUpMockMvc() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext).addFilters(signatureFilter).build();
  }

  @BeforeAll
  static void migrateAndSeed() {
    DATABASE.start();
    Flyway.configure()
        .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
        .locations("filesystem:" + migrationLocation())
        .load()
        .migrate();
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(DATABASE.getJdbcUrl());
    dataSource.setUsername(DATABASE.getUsername());
    dataSource.setPassword(DATABASE.getPassword());
    JdbcTemplate seed = new JdbcTemplate(dataSource);
    materializeStaticData(seed);
    LedgerStaticDataFixtures.insertRates(seed, java.time.LocalDate.of(2026, 9, 10), true);
    LedgerStaticDataFixtures.insertFees(seed, true);
    seedBusinessData(seed);
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
    registry.add("spring.datasource.username", DATABASE::getUsername);
    registry.add("spring.datasource.password", DATABASE::getPassword);
  }

  @BeforeEach
  void clearPayments() {
    jdbcTemplate.update(
        "INSERT INTO journal_entry_type (journal_entry_type_id, code) VALUES (3, 'FEE_PENDING') "
            + "ON CONFLICT (journal_entry_type_id) DO NOTHING");
  }

  @Test
  void createsPaymentAndBalancedPendingFeeEntry() throws Exception {
    String body = body("valid-creation");
    int transactions = count("transaction", "transaction_type_id = 1");
    perform(body, signature(body)).andExpect(status().isCreated());

    assertThat(count("transaction", "transaction_type_id = 1")).isEqualTo(transactions + 1);
    assertThat(count("payment_detail", "true")).isEqualTo(transactions + 1);
    assertThat(count("transaction_event", "transaction_event_type_id = 1"))
        .isEqualTo(transactions + 1);
    assertThat(count("journal_entry", "journal_entry_type_id = 3")).isEqualTo(transactions + 1);
    assertThat(count("journal_entry_line", "true")).isEqualTo(2 * (transactions + 1));
    assertThat(
            jdbcTemplate.queryForObject("SELECT SUM(quantity) FROM journal_entry_line", Long.class))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT transaction_id FROM transaction WHERE reference = 'valid-creation'",
                Long.class))
        .isPositive();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT te.transaction_event_id FROM transaction_event te "
                    + "JOIN transaction t USING (transaction_id) "
                    + "WHERE t.reference = 'valid-creation'",
                Long.class))
        .isPositive();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT je.journal_entry_id FROM journal_entry je "
                    + "JOIN transaction_event te USING (transaction_event_id) "
                    + "JOIN transaction t USING (transaction_id) "
                    + "WHERE t.reference = 'valid-creation'",
                Long.class))
        .isPositive();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT MIN(jel.journal_entry_line_id) FROM journal_entry_line jel "
                    + "JOIN journal_entry je USING (journal_entry_id) "
                    + "JOIN transaction_event te USING (transaction_event_id) "
                    + "JOIN transaction t USING (transaction_id) "
                    + "WHERE t.reference = 'valid-creation'",
                Long.class))
        .isPositive();
  }

  @Test
  void retriesExactlyAndRejectsDifferentFingerprint() throws Exception {
    String body = body("retryable");
    int transactions = count("transaction", "true");
    int writes =
        count("transaction", "true")
            + count("payment_detail", "true")
            + count("transaction_event", "true")
            + count("journal_entry", "true")
            + count("journal_entry_line", "true");
    String first = perform(body, signature(body)).andReturn().getResponse().getContentAsString();

    perform(body, signature(body))
        .andExpect(status().isCreated())
        .andExpect(content().string(first));
    assertThat(
            count("transaction", "true")
                + count("payment_detail", "true")
                + count("transaction_event", "true")
                + count("journal_entry", "true")
                + count("journal_entry_line", "true"))
        .isEqualTo(writes + 6);

    String changed = body.replace("10000", "11000").replace("12000", "13000");
    perform(changed, signature(changed))
        .andExpect(status().isConflict())
        .andExpect(content().json("{\"code\":\"REFERENCE_CONFLICT\"}"));
    assertThat(count("transaction", "true")).isEqualTo(transactions + 1);
    assertThat(
            count("transaction", "true")
                + count("payment_detail", "true")
                + count("transaction_event", "true")
                + count("journal_entry", "true")
                + count("journal_entry_line", "true"))
        .isEqualTo(writes + 6);
  }

  @Test
  void controlledFailuresAreSafeAndRollbackWrites() throws Exception {
    String body = body("failures");
    assertFailure(body.substring(0, body.length() - 1), "INVALID_REQUEST", 400);
    assertFailure(body.replace("DEMO_MERCHANT", "UNKNOWN"), "UNKNOWN_ACCOUNT", 422);
    assertFailure(body.replace("12000", "12001"), "INCONSISTENT_AMOUNTS", 422);

    jdbcTemplate.update("DELETE FROM merchant_fee_configuration");
    assertFailure(body, "MISSING_FEE_CONFIGURATION", 422);
    seedMerchantFees(jdbcTemplate);

    jdbcTemplate.execute(
        "CREATE FUNCTION test_payment_failure() RETURNS trigger LANGUAGE plpgsql AS $$ "
            + "BEGIN RAISE EXCEPTION 'test failure'; END $$");
    jdbcTemplate.execute(
        "CREATE TRIGGER test_payment_failure BEFORE INSERT ON journal_entry "
            + "FOR EACH ROW EXECUTE FUNCTION test_payment_failure()");
    try {
      assertFailure(body, "INTERNAL_ERROR", 500);
    } finally {
      jdbcTemplate.execute("DROP TRIGGER test_payment_failure ON journal_entry");
      jdbcTemplate.execute("DROP FUNCTION test_payment_failure()");
    }
  }

  @Test
  void rejectsMissingMalformedAlteredAndWrongKeyBeforePayloadParsing() throws Exception {
    String invalidJson = "not-json-and-do-not-disclose-this-body";
    assertUnauthorized(performWithoutSignature(invalidJson), invalidJson);
    assertUnauthorized(perform(invalidJson, "malformed-signature"), invalidJson);
    assertUnauthorized(perform(invalidJson, signature(invalidJson) + "altered"), invalidJson);
    assertUnauthorized(
        perform(
            invalidJson,
            HmacSha256.signUtf8(HmacKey.fromUtf8("worker-key"), invalidJson).toBase64()),
        invalidJson);
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transaction", Long.class))
        .isNotNull();
  }

  @Test
  void concurrentIdenticalRequestsCreateOnePayment() throws Exception {
    String body = body("concurrent");
    int transactions = count("transaction", "true");
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> responses = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        responses.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  return perform(body, signature(body)).andReturn().getResponse().getStatus();
                }));
      }
      assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(responses.get(0).get()).isEqualTo(201);
      assertThat(responses.get(1).get()).isEqualTo(201);
    } finally {
      executor.shutdownNow();
    }
    assertThat(count("transaction", "true")).isEqualTo(transactions + 1);
    assertThat(count("payment_detail", "true")).isEqualTo(transactions + 1);
  }

  private void assertFailure(String body, String code, int status) throws Exception {
    int transactions = count("transaction", "true");
    int details = count("payment_detail", "true");
    int events = count("transaction_event", "true");
    int entries = count("journal_entry", "true");
    int lines = count("journal_entry_line", "true");
    perform(body, signature(body))
        .andExpect(status().is(status))
        .andExpect(content().json("{\"code\":\"" + code + "\"}"));
    assertThat(count("transaction", "true")).isEqualTo(transactions);
    assertThat(count("payment_detail", "true")).isEqualTo(details);
    assertThat(count("transaction_event", "true")).isEqualTo(events);
    assertThat(count("journal_entry", "true")).isEqualTo(entries);
    assertThat(count("journal_entry_line", "true")).isEqualTo(lines);
  }

  private static void assertUnauthorized(
      org.springframework.test.web.servlet.ResultActions actions, String body) throws Exception {
    var response = actions.andExpect(status().isUnauthorized()).andReturn().getResponse();
    assertThat(response.getContentAsString()).isEqualTo("{\"code\":\"UNAUTHENTICATED\"}");
    assertThat(response.getContentAsString())
        .doesNotContain(SECRET)
        .doesNotContain("X-Outpost-Signature")
        .doesNotContain(body);
  }

  private static String body(String reference) {
    return VALID_BODY.replace("payment-1", reference);
  }

  private org.springframework.test.web.servlet.ResultActions perform(String body, String sig)
      throws Exception {
    var builder = post("/v1/payment").contentType("application/json").content(body);
    if (sig != null) {
      builder.header("X-Outpost-Signature", sig);
    }
    return mockMvc.perform(builder);
  }

  private org.springframework.test.web.servlet.ResultActions performWithoutSignature(String body)
      throws Exception {
    return mockMvc.perform(post("/v1/payment").contentType("application/json").content(body));
  }

  private static String signature(String body) {
    return HmacSha256.sign(GATEWAY_KEY, body.getBytes(StandardCharsets.UTF_8)).toBase64();
  }

  private int count(String table, String predicate) {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE " + predicate, Integer.class));
  }

  private static void materializeStaticData(JdbcTemplate jdbcTemplate) {
    for (Countries value : Countries.values()) {
      var country = value.getValue();
      jdbcTemplate.update(
          "INSERT INTO country VALUES (?, ?, ?)",
          country.getCountryId(),
          country.getIsoCode(),
          country.getName());
    }
    for (CountrySubdivisions value : CountrySubdivisions.values()) {
      var subdivision = value.getValue();
      jdbcTemplate.update(
          "INSERT INTO country_subdivision VALUES (?, ?, ?, ?)",
          subdivision.getCountrySubdivisionId(),
          subdivision.getCountry().getCountryId(),
          subdivision.getCode(),
          subdivision.getName());
    }
    for (Currencies value : Currencies.values()) {
      var currency = value.getValue();
      jdbcTemplate.update(
          "INSERT INTO currency VALUES (?, ?, ?)",
          currency.getCurrencyId(),
          currency.getCurrencyCode(),
          currency.getExponent());
    }
    for (AccountTypes value : AccountTypes.values()) {
      var type = value.getValue();
      jdbcTemplate.update(
          "INSERT INTO account_type VALUES (?, ?)", type.getAccountTypeId(), type.getCode());
    }
    for (FeeModes value : FeeModes.values()) {
      var mode = value.getValue();
      jdbcTemplate.update(
          "INSERT INTO fee_mode VALUES (?, ?)", mode.getFeeModeId(), mode.getCode());
    }
    for (RegisterTypes value : RegisterTypes.values()) {
      var type = value.getValue();
      jdbcTemplate.update(
          "INSERT INTO register_type VALUES (?, ?)", type.getRegisterTypeId(), type.getCode());
    }
    for (AccountTypeRegisterTypes value : AccountTypeRegisterTypes.values()) {
      var mapping = value.getValue();
      jdbcTemplate.update(
          "INSERT INTO account_type_register_type VALUES (?, ?, ?)",
          mapping.getAccountTypeRegisterTypeId(),
          mapping.getAccountType().getAccountTypeId(),
          mapping.getRegisterType().getRegisterTypeId());
    }
    for (TransactionTypes value : TransactionTypes.values()) {
      var type = value.getValue();
      jdbcTemplate.update(
          "INSERT INTO transaction_type VALUES (?, ?)",
          type.getTransactionTypeId(),
          type.getCode());
    }
    for (TransactionEventTypes value : TransactionEventTypes.values()) {
      var type = value.getValue();
      jdbcTemplate.update(
          "INSERT INTO transaction_event_type VALUES (?, ?, ?)",
          type.getTransactionEventTypeId(),
          type.getCode(),
          type.isRequiresJournalEntry());
    }
    for (JournalEntryTypes value : JournalEntryTypes.values()) {
      var type = value.getValue();
      jdbcTemplate.update(
          "INSERT INTO journal_entry_type VALUES (?, ?)",
          type.getJournalEntryTypeId(),
          type.getCode());
    }
  }

  private static void seedBusinessData(JdbcTemplate jdbcTemplate) {
    long rootType = accountTypeId(AccountTypes.ROOT);
    long platformType = accountTypeId(AccountTypes.PLATFORM);
    long merchantType = accountTypeId(AccountTypes.MERCHANT);
    final long pspType = accountTypeId(AccountTypes.PSP);
    final long taxAuthorityType = accountTypeId(AccountTypes.TAX_AUTHORITY);
    insertAccount(jdbcTemplate, 1, rootType, null, "ROOT", "Outpost Chart Root");
    insertAccount(jdbcTemplate, 100, platformType, 1L, "OUTPOST", "Outpost");
    insertAccount(jdbcTemplate, 200, merchantType, 1L, "DEMO_MERCHANT", "Demo Merchant");
    insertAccount(jdbcTemplate, 210, merchantType, 1L, "DEMO_MERCHANT_2", "Demo Merchant 2");
    insertAccount(jdbcTemplate, 300, pspType, 1L, "DEMO_PSP", "Demo PSP");
    for (Countries value : Countries.values()) {
      var country = value.getValue();
      insertAccount(
          jdbcTemplate,
          1000 + country.getCountryId(),
          taxAuthorityType,
          1L,
          "TAX_AUTHORITY_" + country.getIsoCode(),
          country.getName() + " Tax Authority");
      jdbcTemplate.update(
          "INSERT INTO tax_authority_account VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
          country.getCountryId(),
          1000 + country.getCountryId(),
          taxAuthorityType);
    }
    seedRegisters(jdbcTemplate);
    seedMerchantFees(jdbcTemplate);
  }

  private static void insertAccount(
      JdbcTemplate jdbcTemplate,
      long id,
      long type,
      @Nullable Long parent,
      String code,
      String name) {
    jdbcTemplate.update(
        "INSERT INTO account (account_id, account_type_id, parent_account_id, code, name, "
            + "is_active, created_ts) VALUES (?, ?, ?, ?, ?, true, '2026-01-01T00:00:00Z')",
        id,
        type,
        parent,
        code,
        name);
  }

  private static void seedRegisters(JdbcTemplate jdbcTemplate) {
    List<Long> accountIds =
        jdbcTemplate.queryForList(
            "SELECT account_id FROM account WHERE account_id <> 1", Long.class);
    for (long accountId : accountIds) {
      long accountTypeId =
          Objects.requireNonNull(
              jdbcTemplate.queryForObject(
                  "SELECT account_type_id FROM account WHERE account_id = ?",
                  Long.class,
                  accountId));
      for (AccountTypeRegisterTypes value : AccountTypeRegisterTypes.values()) {
        var mapping = value.getValue();
        if (mapping.getAccountType().getAccountTypeId() == accountTypeId) {
          jdbcTemplate.update(
              "INSERT INTO register (register_id, account_id, register_type_id) VALUES (?, ?, ?) "
                  + "ON CONFLICT DO NOTHING",
              accountId * 100 + mapping.getRegisterType().getRegisterTypeId(),
              accountId,
              mapping.getRegisterType().getRegisterTypeId());
        }
      }
    }
  }

  private static void seedMerchantFees(JdbcTemplate jdbcTemplate) {
    long percentageMode = FeeModes.PERCENTAGE.getValue().getFeeModeId();
    for (long merchantId : List.of(200L, 210L)) {
      for (Currencies value : List.of(Currencies.EUR, Currencies.USD)) {
        jdbcTemplate.update(
            "INSERT INTO merchant_fee_configuration (account_id, account_type_id, currency_id, "
                + "fee_mode_id, fee_rate_bps, fee_fixed) VALUES (?, ?, ?, ?, 500, NULL) "
                + "ON CONFLICT (account_id, currency_id) DO NOTHING",
            merchantId,
            accountTypeId(AccountTypes.MERCHANT),
            value.getValue().getCurrencyId(),
            percentageMode);
      }
    }
  }

  private static long accountTypeId(AccountTypes type) {
    return type.getValue().getAccountTypeId();
  }

  private static String migrationLocation() {
    return System.getProperty("outpost.migration.location");
  }
}
