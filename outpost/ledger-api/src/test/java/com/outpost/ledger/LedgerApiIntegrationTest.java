package com.outpost.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.common.iso.Currencies;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.fx.provider.FxRateProvider;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(
    classes = LedgerApiApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class LedgerApiIntegrationTest {

  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_ledger_api", "outpost_ledger_api", "outpost_ledger_api");

  @Autowired private FxRateProvider fxRateProvider;
  @Autowired private JdbcTemplate jdbcTemplate;
  private final HttpClient httpClient = HttpClient.newHttpClient();

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
  void resolvesSeededRateAfterStartup() {
    assertThat(
            fxRateProvider
                .getRate(
                    Currencies.EUR.getValue(), Currencies.USD.getValue(), LocalDate.of(2026, 9, 10))
                .rate())
        .isEqualByComparingTo("1.2500000000");
  }

  @Test
  void resolutionUsesTheStartupIndex() {
    jdbcTemplate.update("DELETE FROM fx_rate");

    assertThat(
            fxRateProvider
                .getRate(
                    Currencies.EUR.getValue(), Currencies.USD.getValue(), LocalDate.of(2026, 9, 10))
                .rate())
        .isEqualByComparingTo("1.2500000000");
  }

  @Test
  void exposesOnlyHealthProbesOnTheServicePort() {
    assertThat(get("/livez").statusCode()).isEqualTo(200);
    assertThat(get("/readyz").statusCode()).isEqualTo(200);
    assertThat(get("/actuator/metrics").statusCode()).isEqualTo(404);
    assertThat(get("/ledger").statusCode()).isEqualTo(404);
  }

  @Test
  void healthRemainsAvailableWithoutRepeatingStartupValidation() {
    HttpResponse<String> initial = get("/readyz");
    jdbcTemplate.update("DELETE FROM fx_rate");

    HttpResponse<String> repeated = get("/readyz");

    assertThat(initial.statusCode()).isEqualTo(200);
    assertThat(repeated.statusCode()).isEqualTo(200);
  }

  private HttpResponse<String> get(String path) {
    try {
      return httpClient.send(
          HttpRequest.newBuilder(URI.create("http://localhost:8081" + path)).GET().build(),
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
