package com.outpost.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.common.iso.Countries;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.hmac.HmacSha256;
import com.outpost.payment.PspEventCodes;
import com.outpost.payment.PspEventStatuses;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = GatewayApiApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class GatewayApiIntegrationTest {
  private static final String PSP_CODE = "WEBHOOK_PSP";
  private static final String PSP_SECRET = "webhook-test-secret";
  private static final String PAYMENT_REFERENCE = "webhook-payment";
  private static final String FOREIGN_PAYMENT_REFERENCE = "foreign-webhook-payment";
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_gateway_api", "outpost_gateway_api", "outpost_gateway_api");
  private static final RestTemplate REST_TEMPLATE = restTemplateIgnoringErrorStatus();

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbcTemplate;

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
        "INSERT INTO psp_event_code (psp_event_code_id, code) VALUES (?, ?)",
        PspEventCodes.AUTHORISATION.getValue().getPspEventCodeId(),
        PspEventCodes.AUTHORISATION.getValue().getCode());
    seed.update(
        "INSERT INTO psp_event_status (psp_event_status_id, code) VALUES (?, ?)",
        PspEventStatuses.RECEIVED.getValue().getPspEventStatusId(),
        PspEventStatuses.RECEIVED.getValue().getCode());
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
    long order =
        Objects.requireNonNull(
            seed.queryForObject(
                "INSERT INTO merchant_order (order_reference, merchant_reference, account_id, "
                    + "account_type_id, shopper_id, currency_id, net_amount, tax_amount, "
                    + "gross_amount, "
                    + "idempotency_key, "
                    + "created_ts) VALUES ('webhook-order', 'webhook-order', ?, "
                    + "(SELECT account_type_id FROM account_type WHERE code = 'MERCHANT'), ?, ?, "
                    + "100, 0, "
                    + "100, 'webhook-key', now()) "
                    + "RETURNING order_id",
                Long.class,
                merchantAccount,
                shopper,
                currency));
    seedPayment(seed, order, PAYMENT_REFERENCE, pspAccount);
    seedPayment(seed, order, FOREIGN_PAYMENT_REFERENCE, foreignPspAccount);
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
    registry.add("spring.datasource.username", DATABASE::getUsername);
    registry.add("spring.datasource.password", DATABASE::getPassword);
    registry.add(
        "OUTPOST_HMAC_ENCRYPTION_KEY", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    registry.add("OUTPOST_OPERATOR_API_KEY", () -> "fake-operator-key");
  }

  @Test
  void reportsLivenessAndReadinessUpAfterSuccessfulStartup() {
    assertThat(statusOf("/actuator/health/liveness")).isEqualTo("UP");
    assertThat(statusOf("/actuator/health/readiness")).isEqualTo("UP");
  }

  @Test
  void exposesOnlyHealthAndMetricsOnTheServicePort() {
    assertThat(get("/actuator/health").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(get("/actuator/metrics").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(get("/actuator/env").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get("/orders/123").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void repeatedHealthRequestsDoNotRerunStaticDataOrTaxValidation() {
    assertThat(statusOf("/actuator/health/readiness")).isEqualTo("UP");

    jdbcTemplate.update("UPDATE country SET iso_code = 'ZZ' WHERE country_id = 1");
    try {
      assertThat(statusOf("/actuator/health/readiness")).isEqualTo("UP");
      assertThat(statusOf("/actuator/health/readiness")).isEqualTo("UP");
    } finally {
      jdbcTemplate.update("UPDATE country SET iso_code = 'AT' WHERE country_id = 1");
    }
  }

  @Test
  void receivesOneVerifiedWebhookAndRejectsInvalidRequestsWithoutPersistingThem() {
    String valid = payload(PSP_CODE, PAYMENT_REFERENCE, "event-1");
    assertThat(postWebhook(PSP_CODE, valid, signature(valid)).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(postWebhook(PSP_CODE, valid, signature(valid)).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(queueCount()).isEqualTo(1);
    assertThat(receivedEvent())
        .containsEntry("merchant_code", "WEBHOOK_MERCHANT")
        .containsEntry("psp_code", PSP_CODE)
        .containsEntry("status_code", "RECEIVED");

    assertThat(postWebhook(PSP_CODE, valid, "bad").getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            postWebhook(
                    PSP_CODE,
                    payload("OTHER", PAYMENT_REFERENCE, "event-2"),
                    signature(payload("OTHER", PAYMENT_REFERENCE, "event-2")))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(postWebhook("UNKNOWN", valid, "bad").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    String unknownPayment = payload(PSP_CODE, "unknown-payment", "event-3");
    assertThat(
            postWebhook(PSP_CODE, unknownPayment, signature(unknownPayment))
                .getStatusCode()
                .value())
        .isEqualTo(422);
    String foreignPayment = payload(PSP_CODE, FOREIGN_PAYMENT_REFERENCE, "event-4");
    assertThat(
            postWebhook(PSP_CODE, foreignPayment, signature(foreignPayment))
                .getStatusCode()
                .value())
        .isEqualTo(422);
    assertThat(queueCount()).isEqualTo(1);
  }

  @Test
  void rejectsMalformedBodyWithoutSignatureBeforeParsing() {
    jdbcTemplate.update("DELETE FROM psp_event_queue");

    assertThat(postWebhook(PSP_CODE, "{", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(queueCount()).isZero();
  }

  @Test
  void rejectsAuthenticatedMalformedBodyWithoutQueueing() {
    jdbcTemplate.update("DELETE FROM psp_event_queue");
    String malformedBody = "{";

    assertThat(postWebhook(PSP_CODE, malformedBody, signature(malformedBody)).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(queueCount()).isZero();
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

  private static void seedPayment(
      JdbcTemplate jdbcTemplate, long order, String paymentReference, long pspAccount) {
    jdbcTemplate.update(
        "INSERT INTO order_payment (order_id, payment_reference, psp_account_id, "
            + "psp_account_type_id, created_ts) "
            + "VALUES (?, ?, ?, (SELECT account_type_id FROM account_type WHERE code = 'PSP'), "
            + "now())",
        order,
        paymentReference,
        pspAccount);
  }

  private int queueCount() {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject("SELECT count(*) FROM psp_event_queue", Integer.class));
  }

  private Map<String, Object> receivedEvent() {
    return jdbcTemplate.queryForMap(
        "SELECT merchant.code AS merchant_code, psp.code AS psp_code, status.code AS status_code "
            + "FROM psp_event_queue queue "
            + "JOIN account merchant ON merchant.account_id = queue.account_id "
            + "JOIN account psp ON psp.account_id = queue.psp_account_id "
            + "JOIN psp_event_status status ON status.psp_event_status_id = queue.status_id");
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
    RestTemplate restTemplate = new RestTemplate();
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
