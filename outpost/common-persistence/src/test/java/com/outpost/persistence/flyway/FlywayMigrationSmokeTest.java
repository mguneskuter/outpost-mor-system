package com.outpost.persistence.flyway;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.persistence.testfixtures.PostgresTestDatabase;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class FlywayMigrationSmokeTest {

  private static final String MIGRATION_LOCATION = System.getProperty("outpost.migration.location");

  @Test
  void freshDatabaseMigratesAndSecondRunAppliesNothing() {
    try (PostgreSQLContainer<?> database =
        PostgresTestDatabase.startContainer("outpost_smoke", "outpost_smoke", "outpost_smoke")) {
      database.start();

      Flyway flyway =
          Flyway.configure()
              .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
              .locations("filesystem:" + MIGRATION_LOCATION)
              .schemas("public")
              .defaultSchema("public")
              .load();

      MigrateResult first = flyway.migrate();
      MigrateResult second = flyway.migrate();

      assertThat(first.success).isTrue();
      assertThat(second.success).isTrue();
      assertThat(second.migrationsExecuted).isEqualTo(0);
    }
  }
}
