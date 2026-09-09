package com.outpost.platform.staticdata.job;

import com.outpost.persistence.EnableOutpostPersistence;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

/** Boots the job that seeds enum-backed reference data. */
@SpringBootApplication(scanBasePackages = "com.outpost")
@EnableOutpostPersistence(
    mapperPackages = {
      "com.outpost.common.iso.repository",
      "com.outpost.payment.common.repository",
      "com.outpost.account.repository",
      "com.outpost.account.configuration.repository",
      "com.outpost.platform.staticdata.job.repository"
    })
public class EnsureStaticDataJobApplication {

  /** Starts the static-data job. */
  public static void main(String[] args) {
    SpringApplication.run(EnsureStaticDataJobApplication.class, args);
  }

  /** Seeds missing reference-data records after application startup. */
  @Component
  static class EnsureStaticDataRunner implements ApplicationRunner {

    private final EnsureStaticDataJob job;

    EnsureStaticDataRunner(EnsureStaticDataJob job) {
      this.job = job;
    }

    @Override
    public void run(ApplicationArguments args) {
      job.ensure();
    }
  }
}
