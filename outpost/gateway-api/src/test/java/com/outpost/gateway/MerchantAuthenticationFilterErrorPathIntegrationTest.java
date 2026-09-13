package com.outpost.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.gateway.security.repository.MerchantApiKeyRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = GatewayApiApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(MerchantAuthenticationFilterErrorPathIntegrationTest.ThrowingCredentialsConfiguration.class)
class MerchantAuthenticationFilterErrorPathIntegrationTest {
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_gateway_authentication_error",
          "outpost_gateway_authentication_error",
          "outpost_gateway_authentication_error");
  private static final RestTemplate REST_TEMPLATE = restTemplateIgnoringErrorStatus();

  @LocalServerPort private int port;

  @BeforeAll
  static void migrateAndSeed() {
    DATABASE.start();
    Flyway.configure()
        .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
        .locations("filesystem:" + migrationLocation())
        .load()
        .migrate();
    GatewayStaticDataFixtures.materializeAll(
        new JdbcTemplate(
            DataSourceBuilder.create()
                .url(DATABASE.getJdbcUrl())
                .username(DATABASE.getUsername())
                .password(DATABASE.getPassword())
                .build()));
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
    registry.add("spring.datasource.username", DATABASE::getUsername);
    registry.add("spring.datasource.password", DATABASE::getPassword);
    registry.add(
        "OUTPOST_HMAC_ENCRYPTION_KEY", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    registry.add("OUTPOST_OPERATOR_API_KEY", () -> "integration-operator-key");
    registry.add("OUTPOST_LEDGER_GATEWAY_HMAC_SECRET", () -> "gateway-integration-test-key");
  }

  @Test
  void returnsServerErrorWhenCredentialRepositoryFails() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Outpost-Api-Key", "merchant-api-key");

    ResponseEntity<String> response =
        REST_TEMPLATE.exchange(
            "http://localhost:" + port + "/orders/123",
            HttpMethod.GET,
            new HttpEntity<Void>(headers),
            String.class);

    assertThat(response.getStatusCode().value()).isGreaterThanOrEqualTo(500).isNotEqualTo(401);
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

  @TestConfiguration(proxyBeanMethods = false)
  static class ThrowingCredentialsConfiguration {
    @Bean
    @Primary
    MerchantApiKeyRepository throwingMerchantApiKeyRepository() {
      return ignored -> {
        throw new IllegalStateException("credential repository unavailable");
      };
    }
  }
}
