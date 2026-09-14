package com.outpost.platform.staticdata.job;

import com.outpost.framework.persistence.EnableOutpostPersistence;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/** Boots the job that seeds enum-backed reference data. */
@SpringBootApplication(scanBasePackages = "com.outpost")
@EnableOutpostPersistence(
    mapperPackages = {
      "com.outpost.common.iso.repository",
      "com.outpost.payment.common.repository",
      "com.outpost.account.repository",
      "com.outpost.account.configuration.repository",
      "com.outpost.accounting.repository",
      "com.outpost.platform.staticdata.job.repository",
      "com.outpost.platform.staticdata.job.accounting"
    })
public class EnsureStaticDataJobApplication {

  /** Starts the static-data job. */
  public static void main(String[] args) {
    SpringApplication.run(EnsureStaticDataJobApplication.class, args);
  }

  /** Seeds missing reference-data records after application startup. */
  @Bean
  ApplicationRunner ensureStaticDataRunner(EnsureStaticDataJob job) {
    return arguments -> job.ensure();
  }
}
