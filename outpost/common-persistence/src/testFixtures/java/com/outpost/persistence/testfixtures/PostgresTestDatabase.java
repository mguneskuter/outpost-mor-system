package com.outpost.persistence.testfixtures;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Reusable PostgreSQL 18 Testcontainers fixture for Outpost integration tests.
 *
 * <p>It runs a disposable PostgreSQL 18 instance in isolation and registers the standard {@code
 * spring.datasource.*} properties from it. Tests must reuse this fixture instead of connecting to a
 * developer's local Compose database.
 */
public final class PostgresTestDatabase {

  private static final DockerImageName POSTGRES_18 =
      DockerImageName.parse(
              "postgres:18@sha256:4ef4dbc939d61acea57712655ddb4b4ab27419c913f94cca0cd57cb3ea3c2280")
          .asCompatibleSubstituteFor("postgres");

  private static final PostgreSQLContainer<?> CONTAINER =
      new PostgreSQLContainer<>(POSTGRES_18)
          .withDatabaseName("outpost_test")
          .withUsername("outpost_test")
          .withPassword("outpost_test");

  private PostgresTestDatabase() {}

  /** Registers datasource properties from the shared Testcontainers PostgreSQL instance. */
  public static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
    startIfNeeded();
    registry.add("spring.datasource.url", CONTAINER::getJdbcUrl);
    registry.add("spring.datasource.username", CONTAINER::getUsername);
    registry.add("spring.datasource.password", CONTAINER::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  /** Starts the shared container once per JVM if it is not already running. */
  public static void startIfNeeded() {
    if (!CONTAINER.isRunning()) {
      CONTAINER.start();
    }
  }
}
