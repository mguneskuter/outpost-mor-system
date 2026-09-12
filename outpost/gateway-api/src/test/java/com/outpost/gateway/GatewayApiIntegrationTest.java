package com.outpost.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.common.iso.Countries;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
        "INSERT INTO tax_rate (country_id, rate) VALUES (?, ?)",
        Countries.AUSTRIA.getValue().getCountryId(),
        new BigDecimal("0.2000"));
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
