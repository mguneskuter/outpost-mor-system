package com.outpost.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.accounting.api.AccountingQueueRequestTypes;
import com.outpost.accounting.api.AccountingQueueResult;
import com.outpost.accounting.api.AccountingRequestApi;
import com.outpost.accounting.api.BalanceReportApi;
import com.outpost.accounting.api.client.LedgerClientConfiguration;
import com.outpost.accounting.api.client.LedgerHttpServiceGroupConfigurer;
import com.outpost.common.iso.Currencies;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.hmac.HmacSha256;
import com.outpost.fx.provider.FxRateProvider;
import com.outpost.fx.provider.MissingFxRateException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(
    classes = LedgerApiApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class LedgerApiIntegrationTest {

  private static final HmacKey GATEWAY_KEY = HmacKey.fromUtf8("test-gateway-secret");
  private static final Currencies.Currency EUR = Currencies.EUR.getValue();
  private static final Currencies.Currency USD = Currencies.USD.getValue();
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_ledger_api", "outpost_ledger_api", "outpost_ledger_api");

  @Autowired private FxRateProvider fxRateProvider;
  @Autowired private JdbcTemplate jdbcTemplate;
  @LocalManagementPort private int managementPort;
  private final HttpClient httpClient = HttpClient.newHttpClient();

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
    LedgerStaticDataFixtures.insertRates(seed, LocalDate.of(2026, 9, 10), true);
    LedgerStaticDataFixtures.insertFees(seed, true);
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
    registry.add("spring.datasource.username", DATABASE::getUsername);
    registry.add("spring.datasource.password", DATABASE::getPassword);
  }

  @Test
  void resolvesRateStoredAfterStartup() {
    LocalDate rateDate = LocalDate.of(2026, 9, 12);
    jdbcTemplate.update(
        "INSERT INTO fx_rate (base_currency_id, quote_currency_id, rate_date, rate, source)"
            + " VALUES (?, ?, ?, ?, ?)",
        EUR.getCurrencyId(),
        USD.getCurrencyId(),
        rateDate,
        new BigDecimal("1.1734000000"),
        "ECB");

    assertThat(fxRateProvider.getRate(EUR, USD, rateDate).rate()).isEqualByComparingTo("1.1734");
  }

  @Test
  void namesThePairAndDateOfMissingRate() {
    LocalDate rateDate = LocalDate.of(2026, 9, 13);

    assertThatThrownBy(() -> fxRateProvider.getRate(USD, EUR, rateDate))
        .isInstanceOfSatisfying(
            MissingFxRateException.class,
            missing -> {
              assertThat(missing.getBaseCurrency()).isSameAs(USD);
              assertThat(missing.getQuoteCurrency()).isSameAs(EUR);
              assertThat(missing.getRateDate()).isEqualTo(rateDate);
            });
  }

  @Test
  void exposesOnlyHealthProbesOnTheServicePort() {
    assertThat(get("/livez").statusCode()).isEqualTo(200);
    assertThat(get("/readyz").statusCode()).isEqualTo(200);
    assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(401);
    assertThat(get("/actuator/metrics").statusCode()).isEqualTo(401);
    assertThat(get("/ledger").statusCode()).isEqualTo(401);
  }

  @Test
  void servesActuatorEndpointsOnTheManagementPortWithoutSignature() {
    assertThat(getManagement("/actuator/health/readiness").statusCode()).isEqualTo(200);
    assertThat(getManagement("/actuator/metrics").statusCode()).isEqualTo(200);
  }

  @Test
  void deniesNonEndpointPathsOnTheManagementPort() {
    assertThat(getManagement("/v1/report/balance/tax").statusCode()).isEqualTo(403);
  }

  @Test
  void returnsTheRequestErrorStatusToSignedCaller() throws Exception {
    String body = "{}";
    HttpResponse<String> response =
        httpClient.send(
            HttpRequest.newBuilder(URI.create("http://localhost:8081/v1/accounting-request"))
                .header("Content-Type", "text/plain")
                .header("X-Outpost-Signature", HmacSha256.signUtf8(GATEWAY_KEY, body).toBase64())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(415);
  }

  @Test
  void healthRemainsAvailableWithoutRepeatingStartupValidation() {
    HttpResponse<String> initial = get("/readyz");
    jdbcTemplate.update("DELETE FROM fx_rate");

    HttpResponse<String> repeated = get("/readyz");

    assertThat(initial.statusCode()).isEqualTo(200);
    assertThat(repeated.statusCode()).isEqualTo(200);
  }

  @Test
  void acceptsGatewaySignedReportReadThroughTheLedgerClient() {
    try (AnnotationConfigApplicationContext gateway = ledgerClient(GATEWAY_KEY)) {
      BalanceReportApi reports = gateway.getBean(BalanceReportApi.class);

      assertThatCode(reports::tax).doesNotThrowAnyException();
    }
  }

  @Test
  void acceptsGatewaySignedAccountingRequestThroughTheLedgerClient() {
    try (AnnotationConfigApplicationContext gateway = ledgerClient(GATEWAY_KEY)) {
      AccountingRequestApi accountingRequests = gateway.getBean(AccountingRequestApi.class);
      AccountingQueueRequest capture =
          new AccountingQueueRequest(
              AccountingQueueRequestTypes.CAPTURE,
              "unknown-payment",
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

      ResponseEntity<AccountingQueueResult> answer = accountingRequests.submit(capture);

      assertThat(answer.getStatusCode().is2xxSuccessful()).isTrue();
      assertThat(answer.getBody())
          .extracting(AccountingQueueResult::resultCode)
          .isEqualTo(AccountingQueueResult.ACCEPTED);
    }
  }

  @Test
  void rejectsLedgerClientSignedWithAnUnknownKey() {
    try (AnnotationConfigApplicationContext stranger =
        ledgerClient(HmacKey.fromUtf8("unknown-caller-secret"))) {
      BalanceReportApi reports = stranger.getBean(BalanceReportApi.class);

      assertThatThrownBy(reports::tax).isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }
  }

  private static AnnotationConfigApplicationContext ledgerClient(HmacKey signingKey) {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.register(LedgerClientConfiguration.class);
    context.registerBean(
        LedgerHttpServiceGroupConfigurer.class,
        () ->
            new LedgerHttpServiceGroupConfigurer(
                "http://localhost:8081", signingKey, Duration.ofSeconds(1), Duration.ofSeconds(5)));
    context.refresh();
    return context;
  }

  private HttpResponse<String> get(String path) {
    return getFrom("http://localhost:8081", path);
  }

  private HttpResponse<String> getManagement(String path) {
    return getFrom("http://localhost:" + managementPort, path);
  }

  private HttpResponse<String> getFrom(String origin, String path) {
    try {
      return httpClient.send(
          HttpRequest.newBuilder(URI.create(origin + path)).GET().build(),
          HttpResponse.BodyHandlers.ofString());
    } catch (Exception exception) {
      throw new AssertionError("HTTP request failed for " + path, exception);
    }
  }

  private static String migrationLocation() {
    String location = System.getProperty("outpost.migration.location");
    if (location == null || location.isBlank()) {
      throw new IllegalStateException("outpost.migration.location is required");
    }
    return location;
  }
}
