package com.outpost.gateway;

import com.outpost.account.configuration.repository.sanity.MerchantConfigurationStaticDataRepositories;
import com.outpost.account.repository.sanity.AccountConfigurationStaticDataRepositories;
import com.outpost.common.iso.repository.sanity.CommonIsoStaticDataRepositories;
import com.outpost.framework.persistence.EnableOutpostPersistence;
import com.outpost.payment.common.repository.sanity.CommonPaymentStaticDataRepositories;
import com.outpost.platform.staticdata.check.EnableSystemSanityCheck;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Gateway API is Outpost's front-facing API.
 *
 * <p>Gateway authenticates callers and orchestrates orders, PSP webhooks, refunds, and balance
 * reports. This bootstrap currently wires shared persistence and read-only static-data verification
 * while those business capabilities are added.
 */
@SpringBootApplication
@EnableOutpostPersistence(
    mapperPackages = {
      "com.outpost.common.iso.repository.sanity",
      "com.outpost.payment.common.repository.sanity",
      "com.outpost.account.repository.sanity",
      "com.outpost.account.configuration.repository.sanity"
    })
@EnableSystemSanityCheck
@Import({
  CommonIsoStaticDataRepositories.class,
  CommonPaymentStaticDataRepositories.class,
  AccountConfigurationStaticDataRepositories.class,
  MerchantConfigurationStaticDataRepositories.class
})
public class GatewayApiApplication {

  /** Starts the application. */
  public static void main(String[] args) {
    SpringApplication.run(GatewayApiApplication.class, args);
  }
}
