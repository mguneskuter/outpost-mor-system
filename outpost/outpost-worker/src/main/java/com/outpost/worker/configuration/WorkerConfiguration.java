package com.outpost.worker.configuration;

import com.outpost.accounting.queue.AccountingRequestQueue;
import com.outpost.integration.psp.service.PspClient;
import com.outpost.integration.psp.simulator.SimulatorPspClient;
import com.outpost.integration.psp.simulator.repository.PspConfigurationRepository;
import com.outpost.integration.psp.simulator.repository.mybatis.MyBatisPspConfigurationRepository;
import com.outpost.integration.psp.simulator.repository.mybatis.PspConfigurationMapper;
import com.outpost.payment.repository.PaymentOrderRepository;
import com.outpost.payment.repository.PspEventRepository;
import com.outpost.payment.repository.RefundItemRepository;
import com.outpost.payment.repository.mybatis.MyBatisPaymentOrderRepository;
import com.outpost.payment.repository.mybatis.MyBatisPspEventRepository;
import com.outpost.payment.repository.mybatis.MyBatisRefundItemRepository;
import com.outpost.payment.repository.mybatis.PaymentOrderMapper;
import com.outpost.payment.repository.mybatis.PspEventQueueMapper;
import com.outpost.payment.repository.mybatis.RefundItemMapper;
import com.outpost.worker.accounting.AccountingRequestPoller;
import com.outpost.worker.accounting.AccountingRequestProcessor;
import com.outpost.worker.accounting.MerchantRefundWorkflow;
import com.outpost.worker.accounting.client.LedgerPaymentClient;
import com.outpost.worker.accounting.client.ledger.LedgerPaymentHttpClient;
import com.outpost.worker.accounting.repository.LedgerTransactionRepository;
import com.outpost.worker.accounting.repository.mybatis.LedgerTransactionMapper;
import com.outpost.worker.accounting.repository.mybatis.MyBatisLedgerTransactionRepository;
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
@EnableConfigurationProperties({
  PspWorkerProperties.class,
  AccountingWorkerProperties.class,
  LedgerWorkerProperties.class,
  PspClientProperties.class
})
public class WorkerConfiguration {
  @Bean
  Clock workerClock() {
    return Clock.systemUTC();
  }

  @Bean
  PspEventRepository pspEventRepository(PspEventQueueMapper mapper) {
    return new MyBatisPspEventRepository(mapper);
  }

  @Bean
  PspEventProcessor pspEventProcessor(
      PspEventRepository events,
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

  @Bean
  PspConfigurationRepository pspConfigurationRepository(
      PspConfigurationMapper mapper, PspClientProperties properties) {
    return new MyBatisPspConfigurationRepository(
        mapper,
        Math.toIntExact(properties.connectTimeout().toMillis()),
        Math.toIntExact(properties.readTimeout().toMillis()));
  }

  @Bean
  PspClient pspClient(PspConfigurationRepository configurations) {
    return new SimulatorPspClient(configurations);
  }

  @Bean
  PaymentOrderRepository paymentOrderRepository(PaymentOrderMapper mapper) {
    return new MyBatisPaymentOrderRepository(mapper);
  }

  @Bean
  RefundItemRepository refundItemRepository(RefundItemMapper mapper) {
    return new MyBatisRefundItemRepository(mapper);
  }

  @Bean
  LedgerTransactionRepository ledgerTransactionRepository(LedgerTransactionMapper mapper) {
    return new MyBatisLedgerTransactionRepository(mapper);
  }

  @Bean
  LedgerPaymentClient ledgerPaymentClient(
      ObjectMapper objectMapper, LedgerWorkerProperties properties) {
    return new LedgerPaymentHttpClient(
        properties.baseUrl(),
        properties.hmacSecret(),
        properties.connectTimeout(),
        properties.readTimeout(),
        objectMapper);
  }

  @Bean
  MerchantRefundWorkflow merchantRefundWorkflow(
      PaymentOrderRepository orders,
      RefundItemRepository refundItems,
      LedgerTransactionRepository transactions,
      LedgerPaymentClient ledger,
      PspClient psp) {
    return new MerchantRefundWorkflow(orders, refundItems, transactions, ledger, psp);
  }

  @Bean
  AccountingRequestProcessor accountingRequestProcessor(
      AccountingRequestQueue requests,
      LedgerPaymentClient ledger,
      MerchantRefundWorkflow refundWorkflow) {
    return new AccountingRequestProcessor(requests, ledger, refundWorkflow);
  }

  @Bean
  @ConditionalOnProperty(
      name = "outpost.worker.accounting.enabled",
      havingValue = "true",
      matchIfMissing = true)
  AccountingRequestPoller accountingRequestPoller(
      AccountingRequestProcessor processor, AccountingWorkerProperties properties) {
    return new AccountingRequestPoller(
        processor, properties.workerCount(), properties.pollInterval());
  }
}
