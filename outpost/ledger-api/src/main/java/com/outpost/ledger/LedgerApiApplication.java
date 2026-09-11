package com.outpost.ledger;

import com.outpost.accounting.repository.sanity.AccountingStaticDataRepositories;
import com.outpost.framework.persistence.EnableOutpostPersistence;
import com.outpost.platform.staticdata.check.EnableSystemSanityCheck;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/** Configures the Ledger application and its startup reference-data checks. */
@SpringBootApplication
@EnableOutpostPersistence(mapperPackages = "com.outpost.accounting.repository.sanity")
@EnableSystemSanityCheck
@Import(AccountingStaticDataRepositories.class)
public class LedgerApiApplication {

  /** Runs the application context and propagates startup validation failures. */
  public static void main(String[] args) {
    SpringApplication.run(LedgerApiApplication.class, args);
  }
}
