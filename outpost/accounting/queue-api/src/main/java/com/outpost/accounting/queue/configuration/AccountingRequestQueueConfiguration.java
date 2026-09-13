package com.outpost.accounting.queue.configuration;

import com.outpost.accounting.queue.AccountingRequestQueue;
import com.outpost.accounting.queue.repository.mybatis.AccountingRequestQueueMapper;
import com.outpost.framework.persistence.EnableOutpostPersistence;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides the queue service for an importing consumer. */
@Configuration(proxyBeanMethods = false)
@EnableOutpostPersistence(mapperPackages = "com.outpost.accounting.queue.repository.mybatis")
public class AccountingRequestQueueConfiguration {
  /** Creates the transactional queue boundary. */
  @Bean
  AccountingRequestQueue accountingRequestQueue(
      AccountingRequestQueueMapper mapper,
      @Value("${outpost.accounting.queue.lease-duration:PT5M}") Duration leaseDuration) {
    return new AccountingRequestQueue(mapper, leaseDuration);
  }
}
