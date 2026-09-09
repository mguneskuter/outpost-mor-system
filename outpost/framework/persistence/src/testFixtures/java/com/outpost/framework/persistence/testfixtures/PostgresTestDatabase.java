package com.outpost.framework.persistence.testfixtures;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Reusable PostgreSQL 18 Testcontainers fixture for Outpost integration tests.
 *
 * <p>It runs a disposable PostgreSQL instance in isolation and registers the standard {@code
 * spring.datasource.*} properties from it. Tests must reuse this fixture instead of connecting to a
 * developer's local Compose database. The image is read from {@code postgresql-fixture.properties}
 * so it stays in sync with the local Compose tag.
 */
public final class PostgresTestDatabase {

  private static final String FIXTURE_PROPERTIES = "postgresql-fixture.properties";
  private static final String IMAGE_PROPERTY = "postgresql.image";
  private static final String FIXTURE_DATABASE = "outpost_test";
  private static final String FIXTURE_USERNAME = "outpost_test";
  private static final String FIXTURE_PASSWORD = "outpost_test";

  private static final DockerImageName POSTGRES_18 =
      DockerImageName.parse(fixtureImage()).asCompatibleSubstituteFor("postgres");

  private static final PostgreSQLContainer<?> CONTAINER =
      new PostgreSQLContainer<>(POSTGRES_18)
          .withDatabaseName(FIXTURE_DATABASE)
          .withUsername(FIXTURE_USERNAME)
          .withPassword(FIXTURE_PASSWORD);

  private PostgresTestDatabase() {}

  /** Creates a fresh disposable PostgreSQL container from the shared fixture image. */
  public static PostgreSQLContainer<?> startContainer(
      String databaseName, String username, String password) {
    return new PostgreSQLContainer<>(POSTGRES_18)
        .withDatabaseName(databaseName)
        .withUsername(username)
        .withPassword(password);
  }

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

  private static String fixtureImage() {
    Properties properties = new Properties();
    try (InputStream stream =
        PostgresTestDatabase.class.getClassLoader().getResourceAsStream(FIXTURE_PROPERTIES)) {
      if (stream == null) {
        throw new IllegalStateException("Missing test fixture resource: " + FIXTURE_PROPERTIES);
      }
      properties.load(stream);
    } catch (IOException exception) {
      throw new IllegalStateException("Could not load " + FIXTURE_PROPERTIES, exception);
    }
    String image = properties.getProperty(IMAGE_PROPERTY);
    if (image == null || image.isEmpty()) {
      throw new IllegalStateException("Missing '" + IMAGE_PROPERTY + "' in " + FIXTURE_PROPERTIES);
    }
    return image;
  }
}
