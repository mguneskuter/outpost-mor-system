package com.outpost.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.outpost.account.AccountTypes;
import com.outpost.account.configuration.FeeModes;
import com.outpost.accounting.AccountTypeRegisterTypes;
import com.outpost.accounting.JournalEntryTypes;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionTypes;
import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.accounting.api.AccountingQueueRequestTypes;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.hmac.HmacSha256;
import com.outpost.ledger.payment.service.CaptureException;
import com.outpost.ledger.payment.service.CaptureService;
import com.outpost.ledger.payment.service.PaymentCreationException;
import com.outpost.ledger.payment.service.PaymentCreationService;
import com.outpost.ledger.payment.service.PaymentEventException;
import com.outpost.ledger.payment.service.PaymentEventService;
import com.outpost.ledger.payment.service.RefundException;
import com.outpost.ledger.payment.service.RefundService;
import com.outpost.payment.common.Amount;
import jakarta.servlet.Filter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = LedgerApiApplication.class)
class PaymentBookingIntegrationTest {

  private static final HmacKey GATEWAY_KEY = HmacKey.fromUtf8("test-gateway-secret");
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer("outpost_payment_booking", "outpost", "outpost");
  private static final long NET = 10_000L;
  private static final long TAX = 2_000L;
  private static final long GROSS = 12_000L;

  @Autowired private WebApplicationContext applicationContext;

  @Autowired
  @Qualifier(AbstractSecurityWebApplicationInitializer.DEFAULT_FILTER_NAME)
  private Filter securityFilterChain;

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PaymentCreationService paymentCreation;
  @Autowired private PaymentEventService paymentEvents;
  @Autowired private CaptureService captures;
  @Autowired private RefundService refunds;
  private MockMvc mockMvc;

  @BeforeEach
  void setUpMockMvc() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilters(securityFilterChain)
            .build();
  }

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
    materializeStaticData(seed);
    LedgerStaticDataFixtures.insertRates(seed, LocalDate.of(2026, 9, 10), true);
    LedgerStaticDataFixtures.insertFees(seed, true);
    seedBusinessData(seed);
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
    registry.add("spring.datasource.username", DATABASE::getUsername);
    registry.add("spring.datasource.password", DATABASE::getPassword);
  }

  @Test
  void createsPaymentAndBalancedPendingFeeEntry() {
    String reference = "valid-creation";
    int payments = count("transaction", "transaction_type_id = 1");
    int lines = count("journal_entry_line", "true");

    paymentCreation.create(orderCreated(reference));

    assertThat(count("transaction", "transaction_type_id = 1")).isEqualTo(payments + 1);
    assertThat(count("journal_entry_line", "true")).isEqualTo(lines + 2);
    long paymentId = transactionId(reference);
    assertThat(count("payment_detail", "transaction_id = " + paymentId)).isEqualTo(1);
    assertThat(countForTransaction("transaction_event", paymentId)).isEqualTo(1);
    assertThat(countForTransaction("journal_entry", paymentId)).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT jet.code FROM journal_entry je "
                    + "JOIN journal_entry_type jet USING (journal_entry_type_id) "
                    + "JOIN transaction_event te USING (transaction_event_id) "
                    + "WHERE te.transaction_id = ?",
                String.class,
                paymentId))
        .isEqualTo("FEE_PENDING");
    assertThat(sumOfLines(paymentId)).isZero();
  }

  @Test
  void repeatsAnIdenticalCreationWithoutWritingAndRejectsDifferentOne() {
    String reference = "retryable";
    paymentCreation.create(orderCreated(reference));
    Writes writes = writes();

    paymentCreation.create(orderCreated(reference));

    assertThat(writes()).isEqualTo(writes);
    PaymentCreationException conflict =
        catchThrowableOfType(
            PaymentCreationException.class,
            () ->
                paymentCreation.create(
                    orderCreated(
                        reference, "DEMO_MERCHANT", Countries.UNITED_STATES.getValue(), 11_000L)));
    assertThat(conflict.status()).isEqualTo(409);
    assertThat(conflict.code()).isEqualTo("REFERENCE_CONFLICT");
    assertThat(writes()).isEqualTo(writes);
  }

  @Test
  void recordsRefusalOnceAndReleasesThePendingFee() {
    String reference = "event-refused";
    paymentCreation.create(orderCreated(reference));
    long paymentId = transactionId(reference);

    paymentEvents.recordAuthorisation(reference, false);

    assertThat(countForTransaction("transaction_event", paymentId)).isEqualTo(2);
    assertThat(countForTransaction("journal_entry", paymentId)).isEqualTo(2);
    assertThat(eventTypes(paymentId)).contains("REFUSED");
    assertThat(sumOfLines(paymentId)).isZero();
    assertFeeRelease(
        paymentId, TransactionEventTypes.REFUSED.getValue().getTransactionEventTypeId());

    Writes writes = writes();
    paymentEvents.recordAuthorisation(reference, false);
    assertThat(writes()).isEqualTo(writes);
  }

  @Test
  void authorisationRecordsNoFeeReleaseAndAnInvalidTransitionDoesNotWrite() {
    String reference = "event-authorised";
    paymentCreation.create(orderCreated(reference));
    long paymentId = transactionId(reference);

    paymentEvents.recordAuthorisation(reference, true);

    assertThat(countForTransaction("transaction_event", paymentId)).isEqualTo(2);
    assertThat(countForTransaction("journal_entry", paymentId)).isEqualTo(1);
    Writes writes = writes();
    PaymentEventException invalidTransition =
        catchThrowableOfType(
            PaymentEventException.class, () -> paymentEvents.recordAuthorisation(reference, false));
    assertThat(invalidTransition.status()).isEqualTo(409);
    assertThat(invalidTransition.code()).isEqualTo("INVALID_TRANSITION");
    PaymentEventException unknown =
        catchThrowableOfType(
            PaymentEventException.class,
            () -> paymentEvents.recordAuthorisation("event-unknown", true));
    assertThat(unknown.status()).isEqualTo(404);
    assertThat(unknown.code()).isEqualTo("PAYMENT_NOT_FOUND");
    assertThat(writes()).isEqualTo(writes);
  }

  @Test
  void repeatedSuccessfulAuthorisationWritesNothing() {
    String reference = "event-authorised-again";
    paymentCreation.create(orderCreated(reference));
    paymentEvents.recordAuthorisation(reference, true);
    Writes writes = writes();

    paymentEvents.recordAuthorisation(reference, true);

    assertThat(writes()).isEqualTo(writes);
  }

  @Test
  void captureOfAnUnknownPaymentIsRejectedWithoutWrites() {
    Writes writes = writes();

    CaptureException rejected =
        catchThrowableOfType(
            CaptureException.class, () -> captures.capture("capture-unknown-payment", true));

    assertThat(rejected.status()).isEqualTo(404);
    assertThat(writes()).isEqualTo(writes);
  }

  @Test
  void refundOfAnUnknownPaymentIsRejectedWithoutWrites() {
    Writes writes = writes();

    RefundException rejected =
        catchThrowableOfType(
            RefundException.class,
            () -> refunds.refund("refund-unknown-payment", "refund-unknown-payment-ref"));

    assertThat(rejected.status()).isEqualTo(404);
    assertThat(writes()).isEqualTo(writes);
  }

  @Test
  void capturesSuccessfullyWithSixBalancedLinesAndClearsPendingFee() {
    String reference = "capture-success";
    paymentCreation.create(orderCreated(reference));
    paymentEvents.recordAuthorisation(reference, true);

    captures.capture(reference, true);

    long paymentId = transactionId(reference);
    Map<String, Object> capture = captureChild(paymentId);
    long captureId = ((Number) Objects.requireNonNull(capture.get("transaction_id"))).longValue();
    assertThat((String) capture.get("reference")).startsWith("capture-");
    assertThat(capture.get("quantity")).isEqualTo(GROSS);
    assertThat(countForTransaction("transaction_event", captureId)).isEqualTo(1);
    assertThat(countForTransaction("journal_entry", captureId)).isEqualTo(1);
    assertThat(lineCount(captureId)).isEqualTo(6);
    assertThat(sumOfLines(captureId)).isZero();
    assertThat(pendingFeeBalance(paymentId, 200L)).isZero();
    assertThat(pendingFeeBalance(paymentId, 100L)).isZero();
  }

  @Test
  void failedCaptureCreatesChildAndReleasesPendingFee() {
    String reference = "capture-failed";
    paymentCreation.create(orderCreated(reference));
    paymentEvents.recordAuthorisation(reference, true);

    captures.capture(reference, false);

    long paymentId = transactionId(reference);
    long captureId =
        ((Number) Objects.requireNonNull(captureChild(paymentId).get("transaction_id")))
            .longValue();
    assertThat(countForTransaction("transaction_event", captureId)).isEqualTo(1);
    assertThat(eventTypes(captureId)).containsExactly("CAPTURE_FAILED");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT jet.code FROM journal_entry je "
                    + "JOIN journal_entry_type jet USING (journal_entry_type_id) "
                    + "JOIN transaction_event te USING (transaction_event_id) "
                    + "WHERE te.transaction_id = ?",
                String.class,
                captureId))
        .isEqualTo("FEE_RELEASE");
    assertThat(pendingFeeBalance(paymentId, 200L)).isZero();
    assertThat(pendingFeeBalance(paymentId, 100L)).isZero();
  }

  @Test
  void repeatedCaptureOutcomeWritesNothingAndDifferentOutcomeConflicts() {
    String reference = "capture-replay";
    paymentCreation.create(orderCreated(reference));
    paymentEvents.recordAuthorisation(reference, true);
    captures.capture(reference, true);
    Writes writes = writes();

    captures.capture(reference, true);

    assertThat(writes()).isEqualTo(writes);
    CaptureException conflict =
        catchThrowableOfType(CaptureException.class, () -> captures.capture(reference, false));
    assertThat(conflict.status()).isEqualTo(409);
    assertThat(conflict.code()).isEqualTo("CAPTURE_CONFLICT");
    assertThat(writes()).isEqualTo(writes);
  }

  @Test
  void captureBeforeAuthorisationIsRejectedWithoutWrites() {
    String reference = "capture-before-authorisation";
    paymentCreation.create(orderCreated(reference));
    Writes writes = writes();

    CaptureException rejected =
        catchThrowableOfType(CaptureException.class, () -> captures.capture(reference, true));

    assertThat(rejected.status()).isEqualTo(422);
    assertThat(rejected.code()).isEqualTo("INVALID_CAPTURE");
    assertThat(writes()).isEqualTo(writes);
  }

  @Test
  void refundBooksTransactionDetailEventAndThreeLinesAgainstTheCaptureCounterparties() {
    String reference = "refund-booking";
    createCapturedPayment(reference);
    long paymentId = transactionId(reference);

    refunds.refund(reference, "refund-booking-ref");

    long refundId = transactionId("refund-booking-ref");
    assertThat(
            jdbcTemplate.queryForMap(
                "SELECT parent_transaction_id, quantity FROM transaction WHERE transaction_id = ?",
                refundId))
        .containsEntry("parent_transaction_id", paymentId)
        .containsEntry("quantity", GROSS);
    assertThat(
            jdbcTemplate.queryForMap(
                "SELECT net_quantity, tax_quantity FROM refund_detail WHERE transaction_id = ?",
                refundId))
        .containsEntry("net_quantity", NET)
        .containsEntry("tax_quantity", TAX);
    assertThat(eventTypes(refundId)).containsExactly("REFUNDED");
    assertThat(countForTransaction("journal_entry", refundId)).isEqualTo(1);
    assertThat(lineCount(refundId)).isEqualTo(3);
    assertThat(sumOfLines(refundId)).isZero();
    assertThat(lineQuantity(refundId, "PSP_RECEIVABLE")).isEqualTo(-GROSS);
    assertThat(lineQuantity(refundId, "TAX_PAYABLE")).isEqualTo(TAX);
    assertThat(lineQuantity(refundId, "MERCHANT_PAYABLE")).isEqualTo(NET);
    long captureId =
        ((Number) Objects.requireNonNull(captureChild(paymentId).get("transaction_id")))
            .longValue();
    for (String register : List.of("PSP_RECEIVABLE", "TAX_PAYABLE", "MERCHANT_PAYABLE")) {
      assertThat(lineRegisterId(refundId, register)).isEqualTo(lineRegisterId(captureId, register));
    }
  }

  @Test
  void repeatedRefundWritesNothing() {
    String reference = "refund-repeat";
    createCapturedPayment(reference);
    refunds.refund(reference, "refund-repeat-ref");
    Writes writes = writes();

    refunds.refund(reference, "refund-repeat-ref");

    assertThat(writes()).isEqualTo(writes);
  }

  @Test
  void refundOfAnUncapturedPaymentIsRejectedWithoutWrites() {
    String reference = "refund-uncaptured";
    paymentCreation.create(orderCreated(reference));
    paymentEvents.recordAuthorisation(reference, true);
    Writes writes = writes();

    RefundException rejected =
        catchThrowableOfType(
            RefundException.class, () -> refunds.refund(reference, "refund-uncaptured-ref"));

    assertThat(rejected.status()).isEqualTo(422);
    assertThat(rejected.code()).isEqualTo("NOT_CAPTURED");
    assertThat(writes()).isEqualTo(writes);
  }

  @Test
  void secondRefundOfPaymentIsRejectedWithoutWrites() {
    String reference = "refund-second";
    createCapturedPayment(reference);
    refunds.refund(reference, "refund-second-first");
    Writes writes = writes();

    RefundException rejected =
        catchThrowableOfType(
            RefundException.class, () -> refunds.refund(reference, "refund-second-second"));

    assertThat(rejected.status()).isEqualTo(422);
    assertThat(rejected.code()).isEqualTo("ALREADY_REFUNDED");
    assertThat(writes()).isEqualTo(writes);
  }

  @Test
  void paymentCaptureAndRefundCarryTheTimeTheirTransactionStored() {
    String reference = "dated-payment";
    createCapturedPayment(reference);
    refunds.refund(reference, "dated-refund");

    long paymentId = transactionId(reference);
    long captureId =
        ((Number) Objects.requireNonNull(captureChild(paymentId).get("transaction_id")))
            .longValue();
    long refundId = transactionId("dated-refund");
    assertStoredAtItsFirstEventTime(paymentId);
    assertStoredAtItsFirstEventTime(captureId);
    assertStoredAtItsFirstEventTime(refundId);
    assertEntryBookedAndPostedAtItsEventTime(captureId);
    assertEntryBookedAndPostedAtItsEventTime(refundId);
  }

  @Test
  void platformReportShowsEachVisibleRegisterAtItsNormalBalanceInMajorUnits() throws Exception {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    final BigDecimal feeRevenueBefore =
        balanceOrZero(report("/v1/report/balance", today, today), "OUTPOST", "FEE_REVENUE");
    String reference = "platform-report";
    createCapturedPayment(
        reference, orderCreated(reference, "DEMO_MERCHANT_2", Countries.GERMANY.getValue(), NET));

    String report = report("/v1/report/balance", today, today);

    assertThat(accountCodes(report))
        .contains("DEMO_MERCHANT_2", "TAX_AUTHORITY_DE", "OUTPOST")
        .doesNotContain("DEMO_PSP", "ROOT");
    assertThat(balanceAccountCodes(account(report, "DEMO_MERCHANT_2")))
        .containsExactly("MERCHANT_PAYABLE", "PENDING_FEE");
    // Net 100.00 less the 5% fee is owed to the merchant; the pending fee was cleared on capture.
    assertThat(balance(report, "DEMO_MERCHANT_2", "MERCHANT_PAYABLE")).isEqualTo("95.00");
    assertThat(balance(report, "DEMO_MERCHANT_2", "PENDING_FEE")).isEqualTo("0.00");
    assertThat(balance(report, "TAX_AUTHORITY_DE", "TAX_PAYABLE")).isEqualTo("20.00");
    assertThat(balanceOrZero(report, "OUTPOST", "FEE_REVENUE").subtract(feeRevenueBefore))
        .isEqualByComparingTo("5.00");
  }

  @Test
  void merchantReportListsOnlyTheNamedMerchant() throws Exception {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    String reference = "merchant-report";
    createCapturedPayment(reference, orderCreated(reference, "DEMO_MERCHANT_3", NET));

    String report = report("/v1/report/balance/merchant/DEMO_MERCHANT_3", today, today);

    assertThat(accountCodes(report)).containsExactly("DEMO_MERCHANT_3");
    assertThat(balance(report, "DEMO_MERCHANT_3", "MERCHANT_PAYABLE")).isEqualTo("95.00");
    assertThat(accountCodes(report("/v1/report/balance/merchant/NOBODY", today, today))).isEmpty();
  }

  @Test
  void periodWithoutPostingsListsEveryBalanceAccountWithoutBalances() throws Exception {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    LocalDate yesterday = today.minusDays(1);
    createCapturedPayment("period-without-postings");

    String posted = report("/v1/report/balance", today, today);
    String unposted = report("/v1/report/balance", yesterday.minusDays(29), yesterday);

    assertThat(balanceAccountKeys(unposted))
        .isEqualTo(balanceAccountKeys(posted))
        .contains("DEMO_MERCHANT MERCHANT_PAYABLE", "OUTPOST FEE_REVENUE");
    for (JsonNode account : JSON.readTree(unposted).path("accounts")) {
      for (JsonNode balanceAccount : account.path("balance_accounts")) {
        assertThat(balanceAccount.path("balances")).isEmpty();
      }
    }
  }

  @Test
  void balanceReportsRequireTheGatewayKey() throws Exception {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);

    mockMvc
        .perform(
            get("/v1/report/balance")
                .queryParam("from", today.toString())
                .queryParam("to", today.toString()))
        .andExpect(status().isUnauthorized())
        .andExpect(content().json("{\"code\":\"UNAUTHENTICATED\"}"));
  }

  private void createCapturedPayment(String reference) {
    createCapturedPayment(reference, orderCreated(reference));
  }

  private void createCapturedPayment(String reference, AccountingQueueRequest orderCreated) {
    paymentCreation.create(orderCreated);
    paymentEvents.recordAuthorisation(reference, true);
    captures.capture(reference, true);
  }

  private static AccountingQueueRequest orderCreated(String reference) {
    return orderCreated(reference, "DEMO_MERCHANT", NET);
  }

  private static AccountingQueueRequest orderCreated(
      String reference, String merchantCode, long net) {
    return orderCreated(reference, merchantCode, Countries.UNITED_STATES.getValue(), net);
  }

  private static AccountingQueueRequest orderCreated(
      String reference, String merchantCode, Country country, long net) {
    return orderCreated(reference, merchantCode, country, Currencies.EUR.getValue(), net, TAX);
  }

  private static AccountingQueueRequest orderCreated(
      String reference,
      String merchantCode,
      Country country,
      Currency currency,
      long net,
      long tax) {
    return new AccountingQueueRequest(
        AccountingQueueRequestTypes.ORDER_CREATED,
        reference,
        "merchant-" + reference,
        "DEMO_PSP",
        "41",
        null,
        null,
        merchantCode,
        country,
        country.equals(Countries.UNITED_STATES.getValue())
            ? CountrySubdivisions.US_CA.getValue()
            : null,
        new Amount(currency, net),
        new Amount(currency, tax),
        new Amount(currency, net + tax));
  }

  private String report(String path, LocalDate from, LocalDate to) throws Exception {
    return mockMvc
        .perform(
            get(path)
                .queryParam("from", from.toString())
                .queryParam("to", to.toString())
                .header(
                    "X-Outpost-Signature",
                    HmacSha256.sign(GATEWAY_KEY, "".getBytes(StandardCharsets.UTF_8)).toBase64()))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static List<String> accountCodes(String report) throws Exception {
    List<String> codes = new ArrayList<>();
    for (JsonNode account : JSON.readTree(report).path("accounts")) {
      codes.add(account.path("account_code").asText());
    }
    return codes;
  }

  private static JsonNode account(String report, String accountCode) throws Exception {
    return single(JSON.readTree(report).path("accounts"), "account_code", accountCode);
  }

  private static List<String> balanceAccountCodes(JsonNode account) {
    List<String> codes = new ArrayList<>();
    for (JsonNode balanceAccount : account.path("balance_accounts")) {
      codes.add(balanceAccount.path("balance_account_code").asText());
    }
    return codes;
  }

  /** Every account and balance account pair in the report, as {@code "<account> <register>"}. */
  private static Set<String> balanceAccountKeys(String report) throws Exception {
    Set<String> keys = new TreeSet<>();
    for (JsonNode account : JSON.readTree(report).path("accounts")) {
      for (String balanceAccountCode : balanceAccountCodes(account)) {
        keys.add(account.path("account_code").asText() + " " + balanceAccountCode);
      }
    }
    return keys;
  }

  private static String balance(String report, String accountCode, String balanceAccountCode)
      throws Exception {
    JsonNode balanceAccount =
        single(
            account(report, accountCode).path("balance_accounts"),
            "balance_account_code",
            balanceAccountCode);
    return single(balanceAccount.path("balances"), "currency", "EUR").path("balance").asText();
  }

  private static BigDecimal balanceOrZero(
      String report, String accountCode, String balanceAccountCode) throws Exception {
    JsonNode balanceAccount =
        single(
            account(report, accountCode).path("balance_accounts"),
            "balance_account_code",
            balanceAccountCode);
    for (JsonNode balance : balanceAccount.path("balances")) {
      if ("EUR".equals(balance.path("currency").asText())) {
        return new BigDecimal(balance.path("balance").asText());
      }
    }
    return BigDecimal.ZERO;
  }

  private static JsonNode single(JsonNode elements, String field, String value) {
    List<JsonNode> matches = new ArrayList<>();
    for (JsonNode element : elements) {
      if (value.equals(element.path(field).asText())) {
        matches.add(element);
      }
    }
    assertThat(matches).hasSize(1);
    return matches.getFirst();
  }

  private Map<String, Object> captureChild(long paymentId) {
    return jdbcTemplate.queryForMap(
        "SELECT transaction_id, reference, quantity FROM transaction "
            + "WHERE parent_transaction_id = ? AND transaction_type_id = "
            + "(SELECT transaction_type_id FROM transaction_type WHERE code = 'CAPTURE')",
        paymentId);
  }

  private List<String> eventTypes(long transactionId) {
    return jdbcTemplate.queryForList(
        "SELECT tet.code FROM transaction_event te "
            + "JOIN transaction_event_type tet USING (transaction_event_type_id) "
            + "WHERE te.transaction_id = ? ORDER BY te.transaction_event_id",
        String.class,
        transactionId);
  }

  private int lineCount(long transactionId) {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM journal_entry_line jel "
                + "JOIN journal_entry je USING (journal_entry_id) "
                + "JOIN transaction_event te USING (transaction_event_id) "
                + "WHERE te.transaction_id = ?",
            Integer.class,
            transactionId));
  }

  private long sumOfLines(long transactionId) {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(jel.quantity), 0) FROM journal_entry_line jel "
                + "JOIN journal_entry je USING (journal_entry_id) "
                + "JOIN transaction_event te USING (transaction_event_id) "
                + "WHERE te.transaction_id = ?",
            Long.class,
            transactionId));
  }

  private long lineQuantity(long transactionId, String registerType) {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT jel.quantity FROM journal_entry_line jel "
                + "JOIN journal_entry je USING (journal_entry_id) "
                + "JOIN transaction_event te USING (transaction_event_id) "
                + "JOIN register r USING (register_id) "
                + "JOIN register_type rt USING (register_type_id) "
                + "WHERE te.transaction_id = ? AND rt.register_type_code = ?",
            Long.class,
            transactionId,
            registerType));
  }

  private long lineRegisterId(long transactionId, String registerType) {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT jel.register_id FROM journal_entry_line jel "
                + "JOIN journal_entry je USING (journal_entry_id) "
                + "JOIN transaction_event te USING (transaction_event_id) "
                + "JOIN register r USING (register_id) "
                + "JOIN register_type rt USING (register_type_id) "
                + "WHERE te.transaction_id = ? AND rt.register_type_code = ?",
            Long.class,
            transactionId,
            registerType));
  }

  private void assertStoredAtItsFirstEventTime(long transactionId) {
    Map<String, Object> times =
        jdbcTemplate.queryForMap(
            "SELECT t.created_ts, MIN(te.event_ts) AS event_ts FROM transaction t "
                + "JOIN transaction_event te USING (transaction_id) "
                + "WHERE t.transaction_id = ? GROUP BY t.created_ts",
            transactionId);
    assertThat(times.get("created_ts")).isEqualTo(times.get("event_ts"));
  }

  private void assertEntryBookedAndPostedAtItsEventTime(long transactionId) {
    Map<String, Object> times =
        jdbcTemplate.queryForMap(
            "SELECT te.event_ts, je.booked, je.posted FROM transaction_event te "
                + "JOIN journal_entry je USING (transaction_event_id) "
                + "WHERE te.transaction_id = ?",
            transactionId);
    assertThat(times.get("booked")).isEqualTo(times.get("event_ts"));
    assertThat(times.get("posted")).isEqualTo(times.get("event_ts"));
  }

  private long transactionId(String reference) {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT transaction_id FROM transaction WHERE reference = ?", Long.class, reference));
  }

  private int countForTransaction(String table, long transactionId) {
    String column = table.equals("transaction_event") ? "transaction_id" : "te.transaction_id";
    String from =
        table.equals("transaction_event")
            ? "transaction_event"
            : table + " x JOIN transaction_event te USING (transaction_event_id)";
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + from + " WHERE " + column + " = ?",
            Integer.class,
            transactionId));
  }

  private void assertFeeRelease(long transactionId, long eventTypeId) {
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transaction_event te "
                    + "JOIN journal_entry je USING (transaction_event_id) "
                    + "JOIN journal_entry_type jet USING (journal_entry_type_id) "
                    + "WHERE te.transaction_id = ? AND te.transaction_event_type_id = ? "
                    + "AND jet.code = 'FEE_RELEASE'",
                Integer.class,
                transactionId,
                eventTypeId))
        .isEqualTo(1);
    assertThat(pendingFeeBalance(transactionId, 200L)).isZero();
    assertThat(pendingFeeBalance(transactionId, 100L)).isZero();
  }

  private long pendingFeeBalance(long transactionId, long accountId) {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(jel.quantity), 0) FROM transaction t "
                + "JOIN transaction_event te USING (transaction_id) "
                + "JOIN journal_entry je USING (transaction_event_id) "
                + "JOIN journal_entry_line jel USING (journal_entry_id) "
                + "JOIN register r USING (register_id) "
                + "JOIN register_type rt USING (register_type_id) "
                + "WHERE (t.transaction_id = ? OR t.parent_transaction_id = ?) "
                + "AND r.account_id = ? "
                + "AND rt.register_type_code = 'PENDING_FEE'",
            Long.class,
            transactionId,
            transactionId,
            accountId));
  }

  private Writes writes() {
    return new Writes(
        count("transaction", "true"),
        count("payment_detail", "true"),
        count("refund_detail", "true"),
        count("transaction_event", "true"),
        count("journal_entry", "true"),
        count("journal_entry_line", "true"));
  }

  private int count(String table, String predicate) {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE " + predicate, Integer.class));
  }

  /** The row counts a booking may change; equal before and after means nothing was written. */
  private record Writes(
      int transactions,
      int paymentDetails,
      int refundDetails,
      int events,
      int entries,
      int lines) {}

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
    insertAccount(jdbcTemplate, 220, merchantType, 1L, "DEMO_MERCHANT_3", "Demo Merchant 3");
    insertAccount(jdbcTemplate, 230, merchantType, 1L, "DEMO_MERCHANT_4", "Demo Merchant 4");
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
    for (long merchantId : List.of(200L, 210L, 220L, 230L)) {
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
