package com.outpost.persistence.flyway;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Explicit command-line Flyway runner used by {@code make migrate}.
 *
 * <p>This runner lives in the {@code cli} source set, so it is never bundled into the production
 * runtime. It is never invoked from an application's startup path: it connects to the configured
 * database, runs Flyway against the single repository-level migration location, and reports the
 * applied migration summary.
 */
public final class FlywayCli {

  private static final Logger LOGGER = LoggerFactory.getLogger(FlywayCli.class);

  private static final String SCHEMA = "public";
  private static final String LOCATION_PREFIX = "filesystem:";
  private static final String ARG_URL = "OUTPOST_DB_URL";
  private static final String ARG_USERNAME = "OUTPOST_DB_USER";
  private static final String ARG_PASSWORD = "OUTPOST_DB_PASSWORD";
  private static final String MIGRATION_LOCATION_PROPERTY = "outpost.migration.location";

  private FlywayCli() {}

  /**
   * Runs Flyway against the configured database.
   *
   * @param args url, username, password (in that order); all three are required
   */
  public static void main(String[] args) {
    String location = requiredProperty(MIGRATION_LOCATION_PROPERTY);
    String url = requiredArg(args, 0, ARG_URL);
    String username = requiredArg(args, 1, ARG_USERNAME);
    String password = requiredArg(args, 2, ARG_PASSWORD);

    LOGGER.info("Running Flyway against schema '{}' from location {}", SCHEMA, location);
    try {
      Flyway flyway =
          Flyway.configure()
              .dataSource(url, username, password)
              .locations(LOCATION_PREFIX + location)
              .schemas(SCHEMA)
              .defaultSchema(SCHEMA)
              .load();
      MigrateResult result = flyway.migrate();
      LOGGER.info(
          "Flyway migrate complete: schema={}, success={}, migrationsExecuted={}",
          SCHEMA,
          result.success,
          result.migrationsExecuted);
    } catch (RuntimeException exception) {
      LOGGER.error("Flyway migrate failed", exception);
      throw exception;
    }
  }

  private static String requiredProperty(String key) {
    String value = System.getProperty(key);
    if (value == null || value.isEmpty()) {
      throw new IllegalArgumentException(key + " must be provided");
    }
    return value;
  }

  private static String requiredArg(String[] args, int index, String fallbackName) {
    if (index >= args.length) {
      throw new IllegalArgumentException(fallbackName + " must be provided");
    }
    String value = args[index];
    if (value == null || value.isEmpty()) {
      throw new IllegalArgumentException(fallbackName + " must be provided");
    }
    return value;
  }
}
