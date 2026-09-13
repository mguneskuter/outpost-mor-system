package com.outpost.ledger;

import com.outpost.accounting.repository.sanity.AccountingStaticDataRepositories;
import com.outpost.framework.persistence.EnableOutpostPersistence;
import com.outpost.platform.staticdata.check.EnableSystemSanityCheck;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;

/** Configures the Ledger application and its startup reference-data checks. */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableOutpostPersistence(
    mapperPackages = {
      "com.outpost.accounting.repository.sanity",
      "com.outpost.accounting.journalentry.repository.mybatis"
    })
@EnableSystemSanityCheck
@Import(AccountingStaticDataRepositories.class)
public class LedgerApiApplication {

  /** Runs the application context and propagates startup validation failures. */
  public static void main(String[] args) {
    SpringApplication.run(LedgerApiApplication.class, args);
  }
}
