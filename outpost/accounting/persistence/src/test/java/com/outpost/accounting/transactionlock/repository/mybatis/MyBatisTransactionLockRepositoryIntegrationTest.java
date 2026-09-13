package com.outpost.accounting.transactionlock.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.accounting.transactionlock.TransactionLock;
import com.outpost.accounting.transactionlock.repository.TransactionLockRepository;
import com.outpost.framework.persistence.EnableOutpostPersistence;
import com.outpost.framework.persistence.testfixtures.PostgresTestDatabase;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = MyBatisTransactionLockRepositoryIntegrationTest.TestApplication.class)
class MyBatisTransactionLockRepositoryIntegrationTest {
  private static final String REFERENCE = "ref-1";
  private static final Duration LEASE = Duration.ofSeconds(60);

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TransactionLockRepository repository;

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    PostgresTestDatabase.registerDataSourceProperties(registry);
  }

  @BeforeAll
  static void migrate(@Autowired DataSource dataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations("filesystem:" + System.getProperty("outpost.migration.location"))
        .load()
        .migrate();
  }

  @AfterEach
  void releaseLocks() {
    jdbcTemplate.update("DELETE FROM transaction_lock");
  }

  @Test
  void takesFreeLockDatedByTheDatabase() {
    TransactionLock lock = repository.insertTransactionLock(REFERENCE, LEASE).orElseThrow();

    StoredLock stored = storedLock();
    assertThat(lock.originalReference()).isEqualTo(REFERENCE);
    assertThat(lock.lockedAt()).isEqualTo(stored.lockedAt());
    assertThat(lock.leaseUntil()).isEqualTo(stored.leaseUntil());
    assertThat(lock.leaseUntil()).isEqualTo(lock.lockedAt().plus(LEASE));
  }

  @Test
  void refusesLiveLock() {
    assertThat(repository.insertTransactionLock(REFERENCE, LEASE)).isPresent();

    Optional<TransactionLock> second = repository.insertTransactionLock(REFERENCE, LEASE);

    assertThat(second).isEmpty();
  }

  @Test
  void takesOverAnExpiredLock() {
    jdbcTemplate.update(
        "INSERT INTO transaction_lock (original_reference, locked_ts, lease_until_ts) "
            + "VALUES (?, now() - interval '10 minutes', now() - interval '5 minutes')",
        REFERENCE);
    Instant expiredLockedAt = storedLock().lockedAt();

    Optional<TransactionLock> taken = repository.insertTransactionLock(REFERENCE, LEASE);

    assertThat(taken).isPresent();
    assertThat(taken.orElseThrow().lockedAt()).isAfter(expiredLockedAt);
    assertThat(storedLock().lockedAt()).isEqualTo(taken.orElseThrow().lockedAt());
  }

  @Test
  void releasesOnlyTheLockItsHolderTook() {
    assertThat(repository.insertTransactionLock(REFERENCE, LEASE)).isPresent();
    jdbcTemplate.update(
        "UPDATE transaction_lock SET locked_ts = now() - interval '10 minutes', "
            + "lease_until_ts = now() - interval '5 minutes'");
    StoredLock expired = storedLock();
    TransactionLock expiredHolder =
        new TransactionLock(REFERENCE, expired.lockedAt(), expired.leaseUntil());
    TransactionLock takenOver = repository.insertTransactionLock(REFERENCE, LEASE).orElseThrow();

    repository.deleteTransactionLock(expiredHolder);

    assertThat(storedLock().lockedAt()).isEqualTo(takenOver.lockedAt());
    repository.deleteTransactionLock(takenOver);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM transaction_lock WHERE original_reference = ?",
                Integer.class,
                REFERENCE))
        .isZero();
  }

  private StoredLock storedLock() {
    return Objects.requireNonNull(
        jdbcTemplate.queryForObject(
            "SELECT locked_ts, lease_until_ts FROM transaction_lock WHERE original_reference = ?",
            (row, index) ->
                new StoredLock(
                    row.getObject("locked_ts", OffsetDateTime.class).toInstant(),
                    row.getObject("lease_until_ts", OffsetDateTime.class).toInstant()),
            REFERENCE));
  }

  private record StoredLock(Instant lockedAt, Instant leaseUntil) {}

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EnableOutpostPersistence
  static class TestApplication {
    @Bean
    TransactionLockRepository transactionLockRepository(TransactionLockMapper mapper) {
      return new MyBatisTransactionLockRepository(mapper);
    }
  }
}
