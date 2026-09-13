package com.outpost.gateway.configuration;

import com.outpost.account.configuration.repository.MerchantFeeConfigurationRepository;
import com.outpost.account.configuration.repository.MerchantPspRepository;
import com.outpost.account.configuration.repository.mybatis.MerchantFeeConfigurationMapper;
import com.outpost.account.configuration.repository.mybatis.MerchantPspMapper;
import com.outpost.account.configuration.repository.mybatis.MyBatisMerchantFeeConfigurationRepository;
import com.outpost.account.configuration.repository.mybatis.MyBatisMerchantPspRepository;
import com.outpost.account.repository.AccountRepository;
import com.outpost.account.repository.mybatis.MyBatisAccountRepository;
import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.accounting.api.AccountingRequestApi;
import com.outpost.accounting.api.BalanceReportApi;
import com.outpost.accounting.api.client.LedgerClientConfiguration;
import com.outpost.accounting.api.client.LedgerHttpServiceGroupConfigurer;
import com.outpost.framework.queue.QueueProcessor;
import com.outpost.framework.queue.QueueProcessorSettings;
import com.outpost.framework.queue.TimeOrderedQueue;
import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.gateway.accounting.LedgerAccountingRequestSender;
import com.outpost.gateway.order.service.OrderModificationService;
import com.outpost.gateway.order.service.OrderService;
import com.outpost.gateway.paymentmethod.repository.PaymentMethodRepository;
import com.outpost.gateway.paymentmethod.repository.mybatis.MyBatisPaymentMethodRepository;
import com.outpost.gateway.paymentmethod.repository.mybatis.PaymentMethodMapper;
import com.outpost.gateway.psp.api.PspWebhookResponses;
import com.outpost.gateway.psp.api.PspWebhookSignatureFilter;
import com.outpost.gateway.psp.service.PspWebhookService;
import com.outpost.gateway.report.client.LedgerReportClient;
import com.outpost.gateway.report.client.ledger.LedgerReportHttpClient;
import com.outpost.gateway.report.repository.ReportRepository;
import com.outpost.gateway.report.repository.mybatis.MyBatisReportRepository;
import com.outpost.gateway.report.repository.mybatis.ReportMapper;
import com.outpost.gateway.report.service.ReportService;
import com.outpost.gateway.security.AesGcmSecretAdapter;
import com.outpost.gateway.security.MerchantAuthenticationFilter;
import com.outpost.gateway.security.repository.MerchantApiKeyRepository;
import com.outpost.gateway.security.repository.mybatis.MerchantApiKeyMapper;
import com.outpost.gateway.security.repository.mybatis.MyBatisMerchantApiKeyRepository;
import com.outpost.gateway.tax.repository.mybatis.MyBatisTaxRateRepository;
import com.outpost.gateway.tax.repository.mybatis.TaxRateMapper;
import com.outpost.integration.psp.service.PspClient;
import com.outpost.integration.psp.simulator.SimulatorPspClient;
import com.outpost.integration.psp.simulator.repository.PspConfigurationRepository;
import com.outpost.integration.psp.simulator.repository.mybatis.MyBatisPspConfigurationRepository;
import com.outpost.integration.psp.simulator.repository.mybatis.PspConfigurationMapper;
import com.outpost.payment.order.LineTaxCalculator;
import com.outpost.payment.order.repository.OrderRepository;
import com.outpost.payment.refund.repository.RefundRepository;
import com.outpost.payment.repository.mybatis.MyBatisOrderRepository;
import com.outpost.payment.repository.mybatis.MyBatisRefundRepository;
import com.outpost.payment.repository.mybatis.RefundMapper;
import com.outpost.tax.provider.TaxRateProvider;
import com.outpost.tax.provider.cached.CachedTaxRateProvider;
import com.outpost.tax.repository.TaxRateRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

/** Application bean definitions. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
  GatewayLedgerProperties.class,
  GatewayPspClientProperties.class,
  GatewayAccountingQueueProperties.class
})
@Import({OrderService.class, LedgerClientConfiguration.class})
public class ApplicationBeanConfiguration {

  @Bean
  AesGcmSecretAdapter aesGcmSecretAdapter(@Value("${OUTPOST_HMAC_ENCRYPTION_KEY}") String key) {
    return new AesGcmSecretAdapter(key);
  }

  @Bean
  FilterRegistrationBean<MerchantAuthenticationFilter> merchantAuthenticationFilter(
      MerchantApiKeyRepository merchantApiKeys,
      AesGcmSecretAdapter secrets,
      @Value("${OUTPOST_OPERATOR_API_KEY:}") String operatorKey) {
    var registration =
        new FilterRegistrationBean<>(
            new MerchantAuthenticationFilter(merchantApiKeys, operatorKey, secrets));
    registration.setOrder(1);
    return registration;
  }

  @Bean
  MerchantApiKeyRepository merchantApiKeyRepository(MerchantApiKeyMapper mapper) {
    return new MyBatisMerchantApiKeyRepository(mapper);
  }

  @Bean
  TaxRateRepository taxRateRepository(TaxRateMapper mapper) {
    return new MyBatisTaxRateRepository(mapper);
  }

  @Bean
  PspConfigurationRepository pspConfigurationRepository(
      PspConfigurationMapper mapper, GatewayPspClientProperties properties) {
    return new MyBatisPspConfigurationRepository(
        mapper,
        Math.toIntExact(properties.connectTimeout().toMillis()),
        Math.toIntExact(properties.readTimeout().toMillis()));
  }

  @Bean
  PspWebhookResponses pspWebhookResponses(MeterRegistry meterRegistry) {
    return new PspWebhookResponses(meterRegistry);
  }

  @Bean
  FilterRegistrationBean<PspWebhookSignatureFilter> pspWebhookSignatureFilter(
      PspConfigurationRepository configurations,
      ObjectMapper objectMapper,
      PspWebhookResponses responses) {
    var registration =
        new FilterRegistrationBean<>(
            new PspWebhookSignatureFilter(configurations, objectMapper, responses));
    registration.setOrder(2);
    return registration;
  }

  @Bean
  PspWebhookService pspWebhookService(
      OrderRepository orders, TimeOrderedQueue<AccountingQueueRequest> accountingQueue) {
    return new PspWebhookService(orders, accountingQueue);
  }

  /** Creates the tax-rate provider. */
  @Bean
  TaxRateProvider taxRateProvider(TaxRateRepository repository) {
    return new CachedTaxRateProvider(repository);
  }

  @Bean
  OrderRepository orderRepository(
      SqlSessionTemplate sqlSessionTemplate, PlatformTransactionManager transactionManager) {
    return new MyBatisOrderRepository(sqlSessionTemplate, transactionManager);
  }

  @Bean
  AccountRepository accountRepository(SqlSessionTemplate sqlSessionTemplate) {
    return new MyBatisAccountRepository(sqlSessionTemplate);
  }

  @Bean
  MerchantPspRepository merchantPspRepository(MerchantPspMapper mapper) {
    return new MyBatisMerchantPspRepository(mapper);
  }

  @Bean
  MerchantFeeConfigurationRepository merchantFeeConfigurationRepository(
      MerchantFeeConfigurationMapper mapper) {
    return new MyBatisMerchantFeeConfigurationRepository(mapper);
  }

  @Bean
  LineTaxCalculator lineTaxCalculator() {
    return new LineTaxCalculator();
  }

  @Bean
  PspClient pspClient(PspConfigurationRepository configurations) {
    return new SimulatorPspClient(configurations);
  }

  @Bean
  LedgerHttpServiceGroupConfigurer ledgerHttpServiceGroupConfigurer(
      GatewayLedgerProperties properties) {
    return new LedgerHttpServiceGroupConfigurer(
        properties.baseUrl(),
        HmacKey.fromUtf8(properties.hmacSecret()),
        properties.connectTimeout(),
        properties.readTimeout());
  }

  @Bean
  TimeOrderedQueue<AccountingQueueRequest> accountingQueue() {
    return new TimeOrderedQueue<>(Clock.systemUTC());
  }

  @Bean
  LedgerAccountingRequestSender ledgerAccountingRequestSender(AccountingRequestApi api) {
    return new LedgerAccountingRequestSender(api);
  }

  @Bean(initMethod = "start", destroyMethod = "stop")
  QueueProcessor<AccountingQueueRequest> accountingQueueSenders(
      TimeOrderedQueue<AccountingQueueRequest> accountingQueue,
      LedgerAccountingRequestSender sender,
      GatewayAccountingQueueProperties properties) {
    return new QueueProcessor<>(
        "gateway-accounting-queue",
        accountingQueue,
        sender,
        new QueueProcessorSettings(
            properties.workerCount(),
            properties.pollInterval(),
            properties.retryDelay(),
            properties.maxAttempts()));
  }

  @Bean
  RefundRepository refundRepository(RefundMapper mapper) {
    return new MyBatisRefundRepository(mapper);
  }

  @Bean
  OrderModificationService orderModificationService(
      OrderRepository orders, AccountRepository accounts, PspClient psp, RefundRepository refunds) {
    return new OrderModificationService(orders, accounts, psp, refunds);
  }

  @Bean
  LedgerReportClient ledgerReportClient(BalanceReportApi balanceReportApi) {
    return new LedgerReportHttpClient(balanceReportApi);
  }

  @Bean
  ReportRepository reportRepository(ReportMapper mapper) {
    return new MyBatisReportRepository(mapper);
  }

  @Bean
  ReportService reportService(LedgerReportClient ledgerReportClient, ReportRepository repository) {
    return new ReportService(ledgerReportClient, repository);
  }

  @Bean
  PaymentMethodRepository paymentMethodRepository(PaymentMethodMapper mapper) {
    return new MyBatisPaymentMethodRepository(mapper);
  }
}
