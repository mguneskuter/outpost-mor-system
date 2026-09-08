package com.outpost.persistence.flyway;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class FlywayMigrationSmokeTest {

  private static final DockerImageName POSTGRES_18 =
      DockerImageName.parse(
              "postgres:18@sha256:4ef4dbc939d61acea57712655ddb4b4ab27419c913f94cca0cd57cb3ea3c2280")
          .asCompatibleSubstituteFor("postgres");
  private static final String MIGRATION_LOCATION = System.getProperty("outpost.migration.location");

  @Test
  void freshDatabaseMigratesAndSecondRunAppliesNothing() {
    try (PostgreSQLContainer<?> database =
        new PostgreSQLContainer<>(POSTGRES_18)
            .withDatabaseName("outpost_smoke")
            .withUsername("outpost_smoke")
            .withPassword("outpost_smoke")) {
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
