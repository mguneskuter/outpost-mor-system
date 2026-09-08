package com.outpost.persistence.flyway;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

/**
 * Explicit command-line Flyway runner used by {@code make migrate}.
 *
 * <p>This runner is never invoked from an application's startup path. It connects to the configured
 * database, runs Flyway against the single repository-level migration location, and prints the
 * applied migration summary.
 */
public final class FlywayCli {

  private static final String MIGRATION_LOCATION_PROPERTY = "outpost.migration.location";

  private FlywayCli() {}

  /**
   * Runs Flyway against the configured database.
   *
   * @param args url, username, password (in that order); url and username fall back to local
   *     defaults
   */
  public static void main(String[] args) {
    String location = requiredProperty(MIGRATION_LOCATION_PROPERTY);
    String url = arg(args, 0, "jdbc:postgresql://localhost:5432/outpost");
    String username = arg(args, 1, "outpost");
    String password = arg(args, 2, "");
    if (password.isEmpty()) {
      throw new IllegalArgumentException(
          "OUTPOST_DB_PASSWORD must be provided; set it in .env (see .env.example)");
    }

    Flyway flyway =
        Flyway.configure()
            .dataSource(url, username, password)
            .locations("filesystem:" + location)
            .schemas("public")
            .defaultSchema("public")
            .load();
    MigrateResult result = flyway.migrate();
    if (!result.success) {
      throw new IllegalStateException("Flyway migration reported failure");
    }
    System.out.printf(
        "Flyway migrate complete: schema=public, success=%b, migrationsExecuted=%d%n",
        result.success, result.migrationsExecuted);
  }

  private static String requiredProperty(String key) {
    String value = System.getProperty(key);
    if (value == null || value.isEmpty()) {
      throw new IllegalArgumentException(key + " must be provided");
    }
    return value;
  }

  private static String arg(String[] args, int index, String fallback) {
    if (index >= args.length) {
      return fallback;
    }
    String value = args[index];
    if (value == null || value.isEmpty()) {
      return fallback;
    }
    return value;
  }
}
