package com.outpost.gateway;

import com.outpost.persistence.EnableOutpostPersistence;
import com.outpost.platform.staticdata.check.EnableSystemSanityCheck;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Gateway API is Outpost's front-facing API.
 *
 * <p>Gateway authenticates callers and orchestrates orders, PSP webhooks, refunds, and balance
 * reports. This bootstrap currently wires shared persistence and read-only static-data verification
 * while those business capabilities are added.
 */
@SpringBootApplication
@EnableOutpostPersistence
@EnableSystemSanityCheck
public class GatewayApiApplication {

  /** Starts the application. */
  public static void main(String[] args) {
    SpringApplication.run(GatewayApiApplication.class, args);
  }
}
