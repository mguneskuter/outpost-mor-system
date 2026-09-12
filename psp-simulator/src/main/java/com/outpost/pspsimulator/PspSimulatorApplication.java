package com.outpost.pspsimulator;

import com.outpost.pspsimulator.configuration.SimulatorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** Configures the PSP simulator application. */
@SpringBootApplication
@EnableConfigurationProperties(SimulatorProperties.class)
public class PspSimulatorApplication {

  /** Runs the application context and propagates startup validation failures. */
  public static void main(String[] args) {
    SpringApplication.run(PspSimulatorApplication.class, args);
  }
}
