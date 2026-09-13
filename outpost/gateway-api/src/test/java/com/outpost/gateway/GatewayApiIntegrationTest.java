package com.outpost.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.accounting.api.AccountingQueueRequestTypes;
import com.outpost.common.iso.Countries;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.framework.queue.QueuedItem;
import com.outpost.framework.queue.TimeOrderedQueue;
import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.hmac.HmacSha256;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = GatewayApiApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "outpost.gateway.accounting-queue.poll-interval=PT1M")
class GatewayApiIntegrationTest {
  private static final String PSP_CODE = "WEBHOOK_PSP";
  private static final String PSP_SECRET = "webhook-test-secret";
  private static final String ORDER_REFERENCE = "webhook-order";
  private static final String FOREIGN_ORDER_REFERENCE = "foreign-webhook-order";
  private static final String PSP_REFERENCE = "webhook-psp-payment";
  private static final String FOREIGN_PSP_REFERENCE = "foreign-webhook-psp-payment";
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_gateway_api", "outpost_gateway_api", "outpost_gateway_api");
  private static final RestTemplate REST_TEMPLATE = restTemplateIgnoringErrorStatus();
  private static final ObjectMapper JSON = new ObjectMapper();

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TimeOrderedQueue<AccountingQueueRequest> accountingQueue;

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
    GatewayStaticDataFixtures.materializeAll(seed);
    seed.update(
        "INSERT INTO tax_rate (country_id, rate) VALUES (?, ?)",
        Countries.AUSTRIA.getValue().getCountryId(),
        new BigDecimal("0.2000"));
    long merchantAccount =
        Objects.requireNonNull(
            seed.queryForObject(
                "INSERT INTO account (account_type_id, code, name, is_active, created_ts) "
                    + "VALUES ((SELECT account_type_id FROM account_type WHERE code = 'MERCHANT'), "
                    + "'WEBHOOK_MERCHANT', 'Webhook merchant', true, now()) RETURNING account_id",
                Long.class));
    long pspAccount = account(seed, "PSP", PSP_CODE);
    long foreignPspAccount = account(seed, "PSP", "FOREIGN_WEBHOOK_PSP");
    seed.update(
        "INSERT INTO psp_configuration (account_id, account_type_id, base_url, api_key, "
            + "hmac_secret) "
            + "VALUES (?, (SELECT account_type_id FROM account_type WHERE code = 'PSP'), "
            + "'https://psp.example.test', 'api-key', ?)",
        pspAccount,
        PSP_SECRET);
    long shopper =
        Objects.requireNonNull(
            seed.queryForObject(
                "INSERT INTO shopper_detail (email, full_name, country_id) "
                    + "VALUES ('webhook@example.test', 'Webhook shopper', 1) RETURNING shopper_id",
                Long.class));
    long currency =
        Objects.requireNonNull(
            seed.queryForObject(
                "SELECT currency_id FROM currency WHERE currency_code = 'EUR'", Long.class));
    seedOrder(seed, merchantAccount, shopper, currency, ORDER_REFERENCE, pspAccount, PSP_REFERENCE);
    seedOrder(
        seed,
        merchantAccount,
        shopper,
        currency,
        FOREIGN_ORDER_REFERENCE,
        foreignPspAccount,
        FOREIGN_PSP_REFERENCE);
  }

  @AfterEach
  void emptyTheQueue() {
    // The senders drain only every minute here; the queue is shared between tests.
    while (accountingQueue.pollDue().isPresent()) {
      continue;
    }
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
    registry.add("spring.datasource.username", DATABASE::getUsername);
    registry.add("spring.datasource.password", DATABASE::getPassword);
    registry.add(
        "OUTPOST_HMAC_ENCRYPTION_KEY", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    registry.add("OUTPOST_OPERATOR_API_KEY", () -> "fake-operator-key");
    registry.add("OUTPOST_LEDGER_GATEWAY_HMAC_SECRET", () -> "gateway-integration-test-key");
  }

  @Test
  void reportsLivenessAndReadinessUpAfterSuccessfulStartup() {
    assertThat(statusOf("/livez")).isEqualTo("UP");
    assertThat(statusOf("/readyz")).isEqualTo("UP");
  }

  @Test
  void exposesOnlyHealthProbesOnTheServicePort() {
    assertThat(get("/livez").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(get("/readyz").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(get("/actuator/metrics").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get("/orders/123").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void repeatedHealthRequestsDoNotRerunStaticDataOrTaxValidation() {
    assertThat(statusOf("/readyz")).isEqualTo("UP");

    jdbcTemplate.update("UPDATE country SET iso_code = 'ZZ' WHERE country_id = 1");
    try {
      assertThat(statusOf("/readyz")).isEqualTo("UP");
      assertThat(statusOf("/readyz")).isEqualTo("UP");
    } finally {
      jdbcTemplate.update("UPDATE country SET iso_code = 'AT' WHERE country_id = 1");
    }
  }

  @Test
  void queuesOneVerifiedWebhookAndRejectsInvalidRequestsWithoutQueueingThem() {
    String valid = payload(PSP_CODE, ORDER_REFERENCE, PSP_REFERENCE);
    assertResult(postWebhook(PSP_CODE, valid, signature(valid)), 200, "ACCEPTED");
    assertThat(accountingQueue.size()).isEqualTo(1);
    QueuedItem<AccountingQueueRequest> queued = accountingQueue.pollDue().orElseThrow();
    assertThat(queued.payload().type()).isEqualTo(AccountingQueueRequestTypes.AUTHORISATION);
    assertThat(queued.payload().originalReference()).isEqualTo(ORDER_REFERENCE);
    assertThat(queued.payload().pspCode()).isEqualTo(PSP_CODE);
    assertThat(queued.payload().pspReference()).isEqualTo(PSP_REFERENCE);
    assertThat(queued.payload().success()).isTrue();

    assertResult(postWebhook(PSP_CODE, valid, "bad"), 401, "INVALID_SIGNATURE");
    String otherPsp = payload("OTHER", ORDER_REFERENCE, PSP_REFERENCE);
    assertResult(postWebhook(PSP_CODE, otherPsp, signature(otherPsp)), 400, "INVALID_PAYLOAD");
    assertResult(postWebhook("UNKNOWN", valid, "bad"), 404, "UNKNOWN_PSP");
    String unknownOrder = payload(PSP_CODE, "unknown-order", PSP_REFERENCE);
    assertResult(
        postWebhook(PSP_CODE, unknownOrder, signature(unknownOrder)), 200, "UNKNOWN_PAYMENT");
    String foreignOrder = payload(PSP_CODE, FOREIGN_ORDER_REFERENCE, FOREIGN_PSP_REFERENCE);
    assertResult(
        postWebhook(PSP_CODE, foreignOrder, signature(foreignOrder)), 200, "FOREIGN_PAYMENT");
    String otherReference = payload(PSP_CODE, ORDER_REFERENCE, "other-psp-reference");
    assertResult(
        postWebhook(PSP_CODE, otherReference, signature(otherReference)),
        200,
        "PSP_REFERENCE_MISMATCH");
    assertThat(accountingQueue.size()).isZero();
  }

  @Test
  void rejectsMalformedBodyWithoutSignatureBeforeParsing() {
    assertThat(postWebhook(PSP_CODE, "{", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(accountingQueue.size()).isZero();
  }

  @Test
  void rejectsAuthenticatedMalformedBodyWithoutQueueing() {
    String malformedBody = "{";

    assertResult(
        postWebhook(PSP_CODE, malformedBody, signature(malformedBody)), 400, "INVALID_PAYLOAD");
    assertThat(accountingQueue.size()).isZero();
  }

  @Test
  void rejectsAuthenticatedWebhookMissingRequiredFieldWithoutQueueing() {
    String withoutPaymentReference =
        payload(PSP_CODE, ORDER_REFERENCE, PSP_REFERENCE)
            .replace("\"payment_reference\":\"" + ORDER_REFERENCE + "\",", "");

    assertResult(
        postWebhook(PSP_CODE, withoutPaymentReference, signature(withoutPaymentReference)),
        400,
        "INVALID_PAYLOAD");
    assertThat(accountingQueue.size()).isZero();
  }

  private static void assertResult(ResponseEntity<String> response, int status, String code) {
    assertThat(response.getStatusCode().value()).isEqualTo(status);
    assertThat(JSON.readTree(Objects.requireNonNull(response.getBody())).get("code").asString())
        .isEqualTo(code);
  }

  private ResponseEntity<String> postWebhook(String code, String body, @Nullable String signature) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (signature != null) {
      headers.set("X-Outpost-Signature", signature);
    }
    return REST_TEMPLATE.exchange(
        url("/v1/psp/" + code + "/webhook"),
        HttpMethod.POST,
        new HttpEntity<>(body.getBytes(StandardCharsets.UTF_8), headers),
        String.class);
  }

  private static long account(JdbcTemplate jdbcTemplate, String accountTypeCode, String code) {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            "INSERT INTO account (account_type_id, code, name, is_active, created_ts) "
                + "VALUES ((SELECT account_type_id FROM account_type WHERE code = ?), ?, ?, true, "
                + "now()) "
                + "RETURNING account_id",
            Long.class,
            accountTypeCode,
            code,
            code));
  }

  private static void seedOrder(
      JdbcTemplate jdbcTemplate,
      long merchantAccount,
      long shopper,
      long currency,
      String orderReference,
      long pspAccount,
      String pspReference) {
    long orderId =
        Objects.requireNonNull(
            jdbcTemplate.queryForObject(
                "INSERT INTO merchant_order (order_reference, merchant_reference, account_id, "
                    + "account_type_id, shopper_id, currency_id, net_amount, tax_amount, "
                    + "gross_amount, idempotency_key, request_fingerprint, psp_account_id, "
                    + "psp_reference, shopper_country_id, created_ts) "
                    + "VALUES (?, ?, ?, "
                    + "(SELECT account_type_id FROM account_type WHERE code = 'MERCHANT'), ?, ?, "
                    + "100, 0, 100, ?, ?, ?, ?, "
                    + "(SELECT country_id FROM shopper_detail WHERE shopper_id = ?), now()) "
                    + "RETURNING order_id",
                Long.class,
                orderReference,
                orderReference + "-merchant",
                merchantAccount,
                shopper,
                currency,
                orderReference + "-key",
                orderReference + "-fingerprint",
                pspAccount,
                pspReference,
                shopper));
    jdbcTemplate.update(
        "INSERT INTO order_item (order_id, product_type_id, order_line_reference, "
            + "merchant_line_reference, net_amount, tax_amount, tax_rate) "
            + "VALUES (?, (SELECT product_type_id FROM product_type WHERE code = 'DIGITAL_GOODS'), "
            + "?, ?, 100, 0, 0)",
        orderId,
        orderReference + "-line",
        orderReference + "-merchant-line");
  }

  private static String payload(String pspCode, String paymentReference, String pspReference) {
    return """
        {"psp_code":"%s","psp_reference":"%s","psp_refund_reference":null,
        "payment_reference":"%s","event_code":"AUTHORISATION","timestamp":1,"success":true,
        "result_code":"Authorised","amount":100,"currency":"EUR","refund_reference":null}
        """
        .formatted(pspCode, pspReference, paymentReference);
  }

  private static String signature(String body) {
    return HmacSha256.sign(HmacKey.fromUtf8(PSP_SECRET), body.getBytes(StandardCharsets.UTF_8))
        .toBase64();
  }

  private ResponseEntity<String> get(String path) {
    return REST_TEMPLATE.getForEntity(url(path), String.class);
  }

  private String statusOf(String path) {
    ResponseEntity<Map<String, Object>> response =
        REST_TEMPLATE.exchange(
            url(path),
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<Map<String, Object>>() {});
    Object status = Objects.requireNonNull(response.getBody()).get("status");
    return String.valueOf(status);
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }

  private static RestTemplate restTemplateIgnoringErrorStatus() {
    // HttpURLConnection, the RestTemplate default, discards the body of a 401 answer to a POST.
    RestTemplate restTemplate = new RestTemplate(new JdkClientHttpRequestFactory());
    restTemplate.setErrorHandler(
        new DefaultResponseErrorHandler() {
          @Override
          public boolean hasError(ClientHttpResponse response) {
            return false;
          }
        });
    return restTemplate;
  }

  private static String migrationLocation() {
    String location = System.getProperty("outpost.migration.location");
    if (location == null || location.isBlank()) {
      throw new IllegalStateException("outpost.migration.location is required");
    }
    return location;
  }
}
