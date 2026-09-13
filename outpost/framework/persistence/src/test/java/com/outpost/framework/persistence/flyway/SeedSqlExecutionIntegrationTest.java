package com.outpost.framework.persistence.flyway;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.account.AccountTypes;
import com.outpost.account.configuration.FeeModes;
import com.outpost.accounting.AccountTypeRegisterTypes;
import com.outpost.accounting.RegisterTypes;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.Currencies;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class SeedSqlExecutionIntegrationTest {

  private static final String PSP_SIMULATOR_BASE_URL = "http://localhost:8081";
  private static final String PSP_SIMULATOR_API_KEY = "demo-outpost-api-key";
  private static final String PSP_SIMULATOR_HMAC_SECRET = "demo-hmac-secret";

  private static final List<String> REFERENCE_TABLES =
      List.of(
          "account",
          "merchant_api_key",
          "merchant_fee_configuration",
          "merchant_psp",
          "psp_configuration",
          "register",
          "tax_authority_account",
          "tax_rate");

  @Test
  void seedsEveryReferenceTableOnFreshlyMigratedDatabase() throws Exception {
    try (PostgreSQLContainer<?> database =
        PostgresTestDatabase.startContainer(
            "outpost_seed_sql", "outpost_seed_sql", "outpost_seed_sql")) {
      database.start();
      migrate(database);
      try (Connection connection = database.createConnection("")) {
        materialiseStaticData(connection);
        for (Path seedFile : seedFilesInLexicographicOrder()) {
          executeSeedFile(connection, seedFile);
        }
        for (String table : REFERENCE_TABLES) {
          assertThat(count(connection, table)).isGreaterThan(0);
        }
      }
    }
  }

  private static void migrate(PostgreSQLContainer<?> database) {
    Flyway.configure()
        .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
        .locations("filesystem:" + migrationLocation())
        .schemas("public")
        .defaultSchema("public")
        .load()
        .migrate();
  }

  private static List<Path> seedFilesInLexicographicOrder() throws IOException {
    Path seedDirectory = repositoryRoot().resolve("local").resolve("seed_data");
    try (var files = Files.list(seedDirectory)) {
      return files.filter(path -> path.toString().endsWith(".sql")).sorted().toList();
    }
  }

  private static void executeSeedFile(Connection connection, Path seedFile) throws SQLException {
    execute(connection, substitutePsqlVariables(readSql(seedFile)));
  }

  private static String readSql(Path seedFile) {
    try {
      return Files.readString(seedFile, StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static String substitutePsqlVariables(String sql) {
    return sql.replace(":'psp_simulator_base_url'", "'" + PSP_SIMULATOR_BASE_URL + "'")
        .replace(":'psp_simulator_api_key'", "'" + PSP_SIMULATOR_API_KEY + "'")
        .replace(":'psp_simulator_hmac_secret'", "'" + PSP_SIMULATOR_HMAC_SECRET + "'");
  }

  private static void materialiseStaticData(Connection connection) throws SQLException {
    for (Countries country : Countries.values()) {
      Countries.Country value = country.getValue();
      insert(
          connection,
          "INSERT INTO country (country_id, iso_code, name) VALUES (?, ?, ?)",
          value.getCountryId(),
          value.getIsoCode(),
          value.getName());
    }
    for (CountrySubdivisions subdivision : CountrySubdivisions.values()) {
      CountrySubdivisions.CountrySubdivision value = subdivision.getValue();
      insert(
          connection,
          "INSERT INTO country_subdivision (country_subdivision_id, country_id, code, name)"
              + " VALUES (?, ?, ?, ?)",
          value.getCountrySubdivisionId(),
          value.getCountry().getCountryId(),
          value.getCode(),
          value.getName());
    }
    for (Currencies currency : Currencies.values()) {
      Currencies.Currency value = currency.getValue();
      insert(
          connection,
          "INSERT INTO currency (currency_id, currency_code, exponent) VALUES (?, ?, ?)",
          value.getCurrencyId(),
          value.getCurrencyCode(),
          value.getExponent());
    }
    for (AccountTypes accountType : AccountTypes.values()) {
      AccountTypes.AccountType value = accountType.getValue();
      insert(
          connection,
          "INSERT INTO account_type (account_type_id, code) VALUES (?, ?)",
          value.getAccountTypeId(),
          value.getCode());
    }
    for (FeeModes feeMode : FeeModes.values()) {
      FeeModes.FeeMode value = feeMode.getValue();
      insert(
          connection,
          "INSERT INTO fee_mode (fee_mode_id, code) VALUES (?, ?)",
          value.getFeeModeId(),
          value.getCode());
    }
    for (RegisterTypes registerType : RegisterTypes.values()) {
      RegisterTypes.RegisterType value = registerType.getValue();
      insert(
          connection,
          "INSERT INTO register_type (register_type_id, register_type_code) VALUES (?, ?)",
          value.getRegisterTypeId(),
          value.getCode());
    }
    for (AccountTypeRegisterTypes mapping : AccountTypeRegisterTypes.values()) {
      AccountTypeRegisterTypes.AccountTypeRegisterType value = mapping.getValue();
      insert(
          connection,
          "INSERT INTO account_type_register_type"
              + " (account_type_register_type_id, account_type_id, register_type_id)"
              + " VALUES (?, ?, ?)",
          value.getAccountTypeRegisterTypeId(),
          value.getAccountType().getAccountTypeId(),
          value.getRegisterType().getRegisterTypeId());
    }
  }

  private static void insert(Connection connection, String sql, Object... parameters)
      throws SQLException {
    try (var statement = connection.prepareStatement(sql)) {
      for (int index = 0; index < parameters.length; index++) {
        statement.setObject(index + 1, parameters[index]);
      }
      statement.executeUpdate();
    }
  }

  private static void execute(Connection connection, String sql) throws SQLException {
    try (var statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static long count(Connection connection, String table) throws SQLException {
    try (var result = connection.createStatement().executeQuery("SELECT count(*) FROM " + table)) {
      result.next();
      return result.getLong(1);
    }
  }

  private static Path repositoryRoot() {
    Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (directory != null && !Files.exists(directory.resolve("local").resolve("seed.sh"))) {
      directory = directory.getParent();
    }
    return Objects.requireNonNull(directory);
  }

  private static String migrationLocation() {
    String location = System.getProperty("outpost.migration.location");
    if (location == null || location.isBlank()) {
      throw new IllegalStateException("outpost.migration.location is required");
    }
    return location;
  }
}
