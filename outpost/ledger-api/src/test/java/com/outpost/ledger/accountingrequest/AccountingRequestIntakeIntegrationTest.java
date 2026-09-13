package com.outpost.ledger.accountingrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.accounting.api.AccountingQueueRequestTypes;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.framework.queue.TimeOrderedQueue;
import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.hmac.HmacSha256;
import com.outpost.ledger.LedgerApiApplication;
import com.outpost.ledger.LedgerStaticDataFixtures;
import com.outpost.ledger.accountingrequest.service.AccountingRequestRefusedException;
import com.outpost.ledger.accountingrequest.service.AccountingRequestService;
import com.outpost.ledger.accountingrequest.service.LockedAccountingQueueRequest;
import jakarta.servlet.Filter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = LedgerApiApplication.class)
@TestPropertySource(properties = "outpost.ledger.accounting-queue.poll-interval=PT1M")
class AccountingRequestIntakeIntegrationTest {
  private static final HmacKey GATEWAY_KEY = HmacKey.fromUtf8("test-gateway-secret");
  private static final String REFERENCE = "order-intake";
  private static final String CAPTURE_REQUEST =
      """
      {"type":"CAPTURE","original_reference":"%s","merchant_reference":"merchant-order-1",
      "psp_code":"DEMO_PSP","psp_reference":"41","success":true,"refund_reference":null,
      "merchant_code":null,"shopper_country":null,"shopper_country_subdivision":null,
      "net_amount":null,"tax_amount":null,"gross_amount":null}
      """
          .formatted(REFERENCE);
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_accounting_request", "outpost_accounting_request", "outpost_accounting_request");

  @Autowired private WebApplicationContext applicationContext;

  @Autowired
  @Qualifier(AbstractSecurityWebApplicationInitializer.DEFAULT_FILTER_NAME)
  private Filter securityFilterChain;

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TimeOrderedQueue<LockedAccountingQueueRequest> accountingQueue;
  @Autowired private AccountingRequestService accountingRequestService;
  private MockMvc mockMvc;

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
    LedgerStaticDataFixtures.materialize(seed);
    LedgerStaticDataFixtures.insertRates(seed, LocalDate.of(2026, 9, 10), true);
    LedgerStaticDataFixtures.insertFees(seed, true);
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
    registry.add("spring.datasource.username", DATABASE::getUsername);
    registry.add("spring.datasource.password", DATABASE::getPassword);
  }

  @BeforeEach
  void setUpMockMvc() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilters(securityFilterChain)
            .build();
  }

  @AfterEach
  void releaseLocksAndEmptyTheQueue() {
    jdbcTemplate.update("DELETE FROM transaction_lock");
    while (accountingQueue.poll().isPresent()) {
      // The processor drains only every minute here; the queue is shared between tests.
    }
  }

  @Test
  void acceptsValidRequestTakesTheTransactionLockAndQueuesIt() throws Exception {
    submit(CAPTURE_REQUEST, GATEWAY_KEY)
        .andExpect(status().isAccepted())
        .andExpect(
            content()
                .json(
                    """
                    {"success":true,"result_code":"ACCEPTED","type":"CAPTURE",
                    "original_reference":"%s","psp_reference":"41"}
                    """
                        .formatted(REFERENCE)));

    assertThat(lockCount(REFERENCE)).isEqualTo(1);
    assertThat(accountingQueue.size()).isEqualTo(1);
  }

  @Test
  void answersTransactionLockedWhileLiveLockExists() throws Exception {
    jdbcTemplate.update(
        "INSERT INTO transaction_lock (original_reference, locked_ts, lease_until_ts) "
            + "VALUES (?, now(), now() + interval '5 minutes')",
        REFERENCE);

    submit(CAPTURE_REQUEST, GATEWAY_KEY)
        .andExpect(status().isConflict())
        .andExpect(
            content()
                .json(
                    """
                    {"success":false,"result_code":"TRANSACTION_LOCKED","type":"CAPTURE",
                    "original_reference":"%s"}
                    """
                        .formatted(REFERENCE)));

    assertThat(accountingQueue.size()).isZero();
  }

  @Test
  void acceptsRequestOnceTheLeaseHasEnded() throws Exception {
    jdbcTemplate.update(
        "INSERT INTO transaction_lock (original_reference, locked_ts, lease_until_ts) "
            + "VALUES (?, now() - interval '10 minutes', now() - interval '5 minutes')",
        REFERENCE);

    submit(CAPTURE_REQUEST, GATEWAY_KEY).andExpect(status().isAccepted());

    assertThat(accountingQueue.size()).isEqualTo(1);
  }

  @Test
  void queuesExactlyOneOfTwoRequestsRacingForOnePaymentsTransactionLock() throws Exception {
    CyclicBarrier bothReady = new CyclicBarrier(2);
    AccountingQueueRequest request =
        new AccountingQueueRequest(
            AccountingQueueRequestTypes.CAPTURE,
            REFERENCE,
            "merchant-order-1",
            "DEMO_PSP",
            "41",
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    List<Boolean> accepted;
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Boolean> first = executor.submit(() -> accept(bothReady, request));
      Future<Boolean> second = executor.submit(() -> accept(bothReady, request));
      accepted = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
    }

    assertThat(accepted).containsExactlyInAnyOrder(true, false);
    assertThat(lockCount(REFERENCE)).isEqualTo(1);
    assertThat(accountingQueue.size()).isEqualTo(1);
  }

  /** Waits until both submitters are ready, then submits; false means the lock was taken. */
  private boolean accept(CyclicBarrier bothReady, AccountingQueueRequest request) throws Exception {
    bothReady.await(10, TimeUnit.SECONDS);
    try {
      accountingRequestService.accept(request);
      return true;
    } catch (AccountingRequestRefusedException locked) {
      return false;
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("incompleteRequests")
  void rejectsRequestMissingFieldItsTypeRequires(
      String description, String body, String expectedBody) throws Exception {
    submit(body, GATEWAY_KEY)
        .andExpect(status().isBadRequest())
        .andExpect(content().json(expectedBody));

    assertThat(lockCount(REFERENCE)).isZero();
    assertThat(accountingQueue.size()).isZero();
  }

  static Stream<Arguments> incompleteRequests() {
    String refused = "{\"success\":false,\"result_code\":\"INVALID_REQUEST\"}";
    String unreadable = "{\"code\":\"INVALID_REQUEST\"}";
    return Stream.of(
        Arguments.of(
            "CAPTURE without success",
            CAPTURE_REQUEST.replace("\"success\":true", "\"success\":null"),
            refused),
        Arguments.of(
            "REFUND without refund_reference",
            CAPTURE_REQUEST.replace("\"type\":\"CAPTURE\"", "\"type\":\"REFUND\""),
            refused),
        Arguments.of(
            "ORDER_CREATED without currency",
            orderCreated(
                "{\"quantity\":10000}",
                "{\"quantity\":2000}",
                "{\"quantity\":12000}",
                "US",
                "US-CA"),
            unreadable),
        Arguments.of(
            "blank original_reference",
            CAPTURE_REQUEST.replace(
                "\"original_reference\":\"" + REFERENCE + "\"", "\"original_reference\":\" \""),
            refused),
        Arguments.of(
            "subdivision of another country",
            orderCreated(
                "{\"quantity\":10000,\"currency\":\"EUR\"}",
                "{\"quantity\":2000,\"currency\":\"EUR\"}",
                "{\"quantity\":12000,\"currency\":\"EUR\"}",
                "DE",
                "US-CA"),
            refused));
  }

  @Test
  void rejectsRequestSignedWithoutTheGatewayKey() throws Exception {
    submit(CAPTURE_REQUEST, HmacKey.fromUtf8("unknown-caller-secret"))
        .andExpect(status().isUnauthorized());
    submit(CAPTURE_REQUEST, null).andExpect(status().isUnauthorized());

    assertThat(lockCount(REFERENCE)).isZero();
    assertThat(accountingQueue.size()).isZero();
  }

  private static String orderCreated(
      String netAmount, String taxAmount, String grossAmount, String country, String subdivision) {
    return """
        {"type":"ORDER_CREATED","original_reference":"%s","merchant_reference":"merchant-order-1",
        "psp_code":"DEMO_PSP","psp_reference":"41","success":null,"refund_reference":null,
        "merchant_code":"DEMO_MERCHANT","shopper_country":"%s","shopper_country_subdivision":"%s",
        "net_amount":%s,"tax_amount":%s,"gross_amount":%s}
        """
        .formatted(REFERENCE, country, subdivision, netAmount, taxAmount, grossAmount);
  }

  private ResultActions submit(String body, @Nullable HmacKey key) throws Exception {
    var request = post("/v1/accounting-request").contentType("application/json").content(body);
    if (key != null) {
      request.header(
          "X-Outpost-Signature",
          HmacSha256.sign(key, body.getBytes(StandardCharsets.UTF_8)).toBase64());
    }
    return mockMvc.perform(request);
  }

  private int lockCount(String originalReference) {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM transaction_lock WHERE original_reference = ?",
            Integer.class,
            originalReference));
  }
}
