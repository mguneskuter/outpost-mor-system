package com.outpost.worker;

import com.outpost.accounting.queue.configuration.AccountingRequestQueueConfiguration;
import com.outpost.accounting.queue.repository.sanity.AccountingRequestStaticDataRepositories;
import com.outpost.framework.persistence.EnableOutpostPersistence;
import com.outpost.payment.repository.sanity.PspEventStaticDataRepositories;
import com.outpost.platform.staticdata.check.EnableSystemSanityCheck;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/** Configures the Worker application and its startup reference-data checks. */
@SpringBootApplication
@EnableOutpostPersistence(
    mapperPackages = {
      "com.outpost.payment.repository.mybatis",
      "com.outpost.payment.repository.sanity",
      "com.outpost.accounting.queue.repository.mybatis",
      "com.outpost.accounting.queue.repository.sanity",
      "com.outpost.integration.psp.simulator.repository.mybatis",
      "com.outpost.worker.accounting.repository.mybatis"
    })
@EnableSystemSanityCheck
@Import({
  AccountingRequestQueueConfiguration.class,
  AccountingRequestStaticDataRepositories.class,
  PspEventStaticDataRepositories.class
})
public class OutpostWorkerApplication {
  /** Starts the worker. */
  public static void main(String[] args) {
    SpringApplication.run(OutpostWorkerApplication.class, args);
  }
}
