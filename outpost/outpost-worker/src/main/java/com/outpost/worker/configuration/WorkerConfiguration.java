package com.outpost.worker.configuration;

import com.outpost.accounting.queue.AccountingRequestQueue;
import com.outpost.payment.repository.PspEventQueue;
import com.outpost.payment.repository.mybatis.MybatisPspEventQueue;
import com.outpost.payment.repository.mybatis.PspEventQueueMapper;
import com.outpost.worker.psp.PspEventPoller;
import com.outpost.worker.psp.PspEventProcessor;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Wires Worker capability collaborators. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PspWorkerProperties.class)
public class WorkerConfiguration {
  @Bean
  Clock workerClock() {
    return Clock.systemUTC();
  }

  @Bean
  PspEventQueue pspEventQueue(PspEventQueueMapper mapper) {
    return new MybatisPspEventQueue(mapper);
  }

  @Bean
  PspEventProcessor pspEventProcessor(
      PspEventQueue events,
      AccountingRequestQueue requests,
      ObjectMapper objectMapper,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    return new PspEventProcessor(
        events, requests, objectMapper, new TransactionTemplate(transactionManager), clock);
  }

  @Bean
  @ConditionalOnProperty(
      name = "outpost.worker.psp.enabled",
      havingValue = "true",
      matchIfMissing = true)
  PspEventPoller pspEventPoller(PspEventProcessor processor, PspWorkerProperties properties) {
    return new PspEventPoller(processor, properties.workerCount(), properties.pollInterval());
  }
}
