package com.outpost.pspsimulator;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** A disposable PostgreSQL 18 instance for the simulator's integration tests. */
public final class SimulatorTestDatabase {

  private SimulatorTestDatabase() {}

  /** Creates a fresh container; the caller owns starting it. */
  public static PostgreSQLContainer<?> start() {
    return new PostgreSQLContainer<>(DockerImageName.parse("postgres:18"))
        .withDatabaseName("psp_simulator_test")
        .withUsername("psp_simulator_test")
        .withPassword("psp_simulator_test");
  }
}
