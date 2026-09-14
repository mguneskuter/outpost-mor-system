package com.outpost.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.account.configuration.repository.MerchantApiKeyRepository;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.hmac.HmacSha256;
import com.outpost.gateway.GatewayApiApplication;
import com.outpost.gateway.GatewayStaticDataFixtures;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = GatewayApiApplication.class)
@Import(MerchantApiKeyAuthenticationIntegrationTest.AuthenticationProbeConfiguration.class)
class MerchantApiKeyAuthenticationIntegrationTest {
  private static final String API_KEY = "demo-outpost-api-key";
  private static final String HMAC_SECRET = "demo-hmac-secret";
  private static final String API_KEY_SHA256_HEX =
      "8e76b4677297200712e7f3e1348767a1fb76e1b43072209a2726e0057f8e36c6";
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_gateway_merchant_api_key",
          "outpost_gateway_merchant_api_key",
          "outpost_gateway_merchant_api_key");

  @Autowired private MerchantApiKeyRepository repository;
  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private FilterRegistrationBean<MerchantAuthenticationFilter> filterRegistration;
  private MockMvc mockMvc;

  @BeforeEach
  void createMockMvc() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .addFilters(Objects.requireNonNull(filterRegistration.getFilter()))
            .build();
  }

  @BeforeAll
  static void migrateAndSeed() throws SQLException {
    DATABASE.start();
    Flyway.configure()
        .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
        .locations("filesystem:" + migrationLocation())
        .schemas("public")
        .defaultSchema("public")
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
        "INSERT INTO account (account_type_id, code, name, is_active, created_ts) "
            + "VALUES ((SELECT account_type_id FROM account_type WHERE code = 'MERCHANT'), "
            + "'DEMO_MERCHANT', 'Demo Merchant', true, now())");
    try (Connection connection = DATABASE.createConnection("")) {
      execute(connection, Files.readString(seedFile(), StandardCharsets.UTF_8));
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
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
  void findsActiveCredentialsSeededForApiKeyHash() {
    var credentials = repository.findActiveMerchantApiKeyByApiKeyHash(API_KEY_SHA256_HEX);

    assertThat(credentials).isPresent();
    assertThat(Objects.requireNonNull(credentials.orElse(null)).encryptedHmacSecret())
        .isEqualTo("MTIzNDU2Nzg5MDEypG6klax6DJlGPy5yHDDXUn370yQJZ3t0WBCnZ/UkxYA=");
  }

  @Test
  void authenticatesSignedRequestWithSeededMerchantKey() throws Exception {
    long accountId =
        repository
            .findActiveMerchantApiKeyByApiKeyHash(API_KEY_SHA256_HEX)
            .orElseThrow()
            .accountId();
    String signature = HmacSha256.sign(HmacKey.fromUtf8(HMAC_SECRET), new byte[0]).toBase64();

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/authentication-probe")
                .header("X-Outpost-Api-Key", API_KEY)
                .header("X-Outpost-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.accountId").value(accountId))
        .andExpect(MockMvcResultMatchers.jsonPath("$.type").value("MERCHANT"));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class AuthenticationProbeConfiguration {
    @Bean
    AuthenticationProbeController authenticationProbeController() {
      return new AuthenticationProbeController();
    }
  }

  @RestController
  static class AuthenticationProbeController {
    @GetMapping("/authentication-probe")
    GatewayPrincipal probe(HttpServletRequest request) {
      return (GatewayPrincipal)
          request.getAttribute(MerchantAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
    }
  }

  private static void execute(Connection connection, String sql) throws SQLException {
    try (var statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static Path seedFile() {
    Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (directory != null && !Files.exists(directory.resolve("local").resolve("seed.sh"))) {
      directory = directory.getParent();
    }
    return Objects.requireNonNull(directory).resolve("local/seed_data/merchant_api_key.sql");
  }

  private static String migrationLocation() {
    String location = System.getProperty("outpost.migration.location");
    if (location == null || location.isBlank()) {
      throw new IllegalStateException("outpost.migration.location is required");
    }
    return location;
  }
}
