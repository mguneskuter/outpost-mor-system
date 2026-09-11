package com.outpost.platform.staticdata.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = EnsureStaticDataJobApplication.class)
class EnsureStaticDataOperatorIntegrationTest {
  private static final PostgreSQLContainer<?> DATABASE =
      PostgresTestDatabase.startContainer(
          "outpost_static_data", "outpost_static_data", "outpost_static_data");

  static {
    DATABASE.start();
    Flyway.configure()
        .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
        .locations("filesystem:" + migrationLocation())
        .load()
        .migrate();
  }

  private final JdbcTemplate jdbcTemplate;
  private final EnsureStaticDataJob job;

  @Autowired
  EnsureStaticDataOperatorIntegrationTest(JdbcTemplate jdbcTemplate, EnsureStaticDataJob job) {
    this.jdbcTemplate = jdbcTemplate;
    this.job = job;
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
    registry.add("spring.datasource.username", DATABASE::getUsername);
    registry.add("spring.datasource.password", DATABASE::getPassword);
  }

  @BeforeEach
  void createFixtureTable() {
    jdbcTemplate.update("DELETE FROM transaction_event_type WHERE transaction_event_type_id = 500");
    jdbcTemplate.update(
        "INSERT INTO transaction_event_type VALUES (?, ?, ?) "
            + "ON CONFLICT (transaction_event_type_id) DO NOTHING",
        5L,
        "CAPTURED",
        true);
    jdbcTemplate.update(
        "UPDATE transaction_event_type SET code = ?, requires_journal_entry = ? "
            + "WHERE transaction_event_type_id = 5",
        "CAPTURED",
        true);
    jdbcTemplate.execute("DROP TABLE IF EXISTS ensure_operator_fixture");
    jdbcTemplate.execute(
        "CREATE TABLE ensure_operator_fixture (fixture_id BIGINT PRIMARY KEY, code TEXT NOT NULL)");
  }

  @Test
  void insertsMissingFixtureRowsAndIsIdempotent() {
    var operator = fixtureOperator();

    operator.ensure();
    operator.ensure();

    assertThat(jdbcTemplate.queryForList("SELECT fixture_id, code FROM ensure_operator_fixture"))
        .containsExactlyInAnyOrder(
            java.util.Map.of("fixture_id", 101L, "code", "ONE"),
            java.util.Map.of("fixture_id", 202L, "code", "TWO"));
  }

  @Test
  void failsClosedOnDivergence() {
    jdbcTemplate.update("INSERT INTO ensure_operator_fixture VALUES (?, ?)", 101L, "WRONG");

    assertThatThrownBy(() -> fixtureOperator().ensure())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ensure_operator_fixture");
  }

  @Test
  void insertsMissingAccountingRowAndIsIdempotent() {
    jdbcTemplate.update("DELETE FROM journal_entry_type WHERE journal_entry_type_id = 2");

    job.ensure();
    job.ensure();

    assertThat(
            jdbcTemplate.queryForMap(
                "SELECT journal_entry_type_id, code FROM journal_entry_type "
                    + "WHERE journal_entry_type_id = 2"))
        .containsEntry("journal_entry_type_id", 2L)
        .containsEntry("code", "REFUND");
  }

  @Test
  void failsOnDivergentAccountingCode() {
    jdbcTemplate.update(
        "UPDATE transaction_event_type SET code = ? WHERE transaction_event_type_id = 5", "WRONG");

    assertThatThrownBy(job::ensure).isInstanceOf(IllegalStateException.class);
    assertThat(transactionEventRow()).containsEntry("code", "WRONG");
  }

  @Test
  void failsOnDivergentAccountingBookingFlag() {
    jdbcTemplate.update(
        "UPDATE transaction_event_type SET requires_journal_entry = false "
            + "WHERE transaction_event_type_id = 5");

    assertThatThrownBy(job::ensure).isInstanceOf(IllegalStateException.class);
    assertThat(transactionEventRow()).containsEntry("requires_journal_entry", false);
  }

  @Test
  void failsOnDivergentAccountingId() {
    jdbcTemplate.update(
        "UPDATE transaction_event_type SET transaction_event_type_id = 500 "
            + "WHERE transaction_event_type_id = 5");

    assertThatThrownBy(job::ensure).isInstanceOf(IllegalStateException.class);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transaction_event_type WHERE transaction_event_type_id = 500",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void materialisesThePendingFeeAccountingValues() {
    jdbcTemplate.update("DELETE FROM register_type WHERE register_type_id = 8");
    jdbcTemplate.update("DELETE FROM journal_entry_type WHERE journal_entry_type_id IN (3, 4)");

    job.ensure();

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT register_type_code FROM register_type WHERE register_type_id = 8",
                String.class))
        .isEqualTo("PENDING_FEE");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT code FROM journal_entry_type WHERE journal_entry_type_id = 3",
                String.class))
        .isEqualTo("FEE_PENDING");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT code FROM journal_entry_type WHERE journal_entry_type_id = 4",
                String.class))
        .isEqualTo("FEE_RELEASE");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT requires_journal_entry FROM transaction_event_type "
                    + "WHERE transaction_event_type_id = 1",
                Boolean.class))
        .isTrue();
  }

  private Map<String, Object> transactionEventRow() {
    return jdbcTemplate.queryForMap(
        "SELECT code, requires_journal_entry FROM transaction_event_type "
            + "WHERE transaction_event_type_id = 5");
  }

  private EnsureStaticDataOperator<FixtureValues, FixtureValue, FixtureRecord> fixtureOperator() {
    var repository =
        new FixtureRepository(
            jdbcTemplate, List.of(new FixtureRecord(101L, "ONE"), new FixtureRecord(202L, "TWO")));
    return new EnsureStaticDataOperator<>(
        repository,
        record ->
            jdbcTemplate.update(
                "INSERT INTO ensure_operator_fixture VALUES (?, ?)", record.id(), record.code()));
  }

  private static String migrationLocation() {
    String location = System.getProperty("outpost.migration.location");
    if (location == null || location.isBlank()) {
      throw new IllegalStateException("outpost.migration.location is required");
    }
    return location;
  }

  @SuppressWarnings("ImmutableEnumChecker")
  private enum FixtureValues {
    ONE(new FixtureValue(101L, "ONE")),
    TWO(new FixtureValue(202L, "TWO"));

    private final FixtureValue value;

    FixtureValues(FixtureValue value) {
      this.value = value;
    }
  }

  private record FixtureValue(long id, String code) {}

  private record FixtureRecord(long id, String code) {}

  private static final class FixtureRepository
      implements com.outpost.platform.staticdata.StaticDataRepository<
          FixtureValues, FixtureValue, FixtureRecord> {
    private final JdbcTemplate jdbcTemplate;
    private final List<FixtureRecord> expected;

    private FixtureRepository(JdbcTemplate jdbcTemplate, List<FixtureRecord> expected) {
      this.jdbcTemplate = jdbcTemplate;
      this.expected = expected;
    }

    @Override
    public Class<FixtureValues> staticDataEnum() {
      return FixtureValues.class;
    }

    @Override
    public String table() {
      return "ensure_operator_fixture";
    }

    @Override
    public FixtureValue enumValue(FixtureValues constant) {
      return constant.value;
    }

    @Override
    public FixtureRecord toDatabaseRecord(FixtureValue value) {
      return new FixtureRecord(value.id(), value.code());
    }

    @Override
    public FixtureValue toDomainValue(FixtureRecord record) {
      return new FixtureValue(record.id(), record.code());
    }

    @Override
    public List<FixtureRecord> findAll() {
      return jdbcTemplate.query(
          "SELECT fixture_id, code FROM ensure_operator_fixture",
          (resultSet, rowNum) -> new FixtureRecord(resultSet.getLong(1), resultSet.getString(2)));
    }

    @Override
    public List<FixtureRecord> expectedRecords() {
      return expected;
    }

    @Override
    public long id(FixtureRecord record) {
      return record.id();
    }
  }
}
