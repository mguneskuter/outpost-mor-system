package com.outpost.outpostapi;

import com.outpost.persistence.EnableOutpostPersistence;
import com.outpost.platform.staticdata.check.EnableSystemSanityCheck;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Outpost API deployable bootstrap.
 *
 * <p>It currently exposes no business endpoints, controllers, repositories, mappers, or domain
 * services. It only proves that a deployable consumes {@code common-persistence}: the datasource
 * comes from the {@code spring.datasource.*} properties and Spring Boot creates the single Hikari
 * pool.
 */
@SpringBootApplication
@EnableOutpostPersistence
@EnableSystemSanityCheck
public class OutpostApiApplication {

  /** Starts the application. */
  public static void main(String[] args) {
    SpringApplication.run(OutpostApiApplication.class, args);
  }
}
