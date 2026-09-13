package com.outpost.gateway.configuration;

import com.outpost.account.configuration.repository.MerchantFeeConfigurationRepository;
import com.outpost.account.configuration.repository.MerchantPspRepository;
import com.outpost.account.configuration.repository.mybatis.MerchantFeeConfigurationMapper;
import com.outpost.account.configuration.repository.mybatis.MerchantPspMapper;
import com.outpost.account.configuration.repository.mybatis.MyBatisMerchantFeeConfigurationRepository;
import com.outpost.account.configuration.repository.mybatis.MyBatisMerchantPspRepository;
import com.outpost.account.repository.AccountRepository;
import com.outpost.account.repository.mybatis.MyBatisAccountRepository;
import com.outpost.accounting.api.BalanceReportApi;
import com.outpost.accounting.api.PaymentApi;
import com.outpost.accounting.api.client.LedgerClientConfiguration;
import com.outpost.accounting.api.client.LedgerHttpServiceGroupConfigurer;
import com.outpost.accounting.queue.AccountingRequestQueue;
import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.gateway.order.client.LedgerClient;
import com.outpost.gateway.order.client.ledger.LedgerHttpClient;
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
import com.outpost.payment.repository.PspEventRepository;
import com.outpost.payment.repository.mybatis.MyBatisOrderRepository;
import com.outpost.payment.repository.mybatis.MyBatisPspEventRepository;
import com.outpost.payment.repository.mybatis.PspEventQueueMapper;
import com.outpost.tax.provider.TaxRateProvider;
import com.outpost.tax.provider.cached.CachedTaxRateProvider;
import com.outpost.tax.repository.TaxRateRepository;
import io.micrometer.core.instrument.MeterRegistry;
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
@EnableConfigurationProperties({GatewayLedgerProperties.class, GatewayPspClientProperties.class})
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
  PspEventRepository pspEventRepository(PspEventQueueMapper mapper) {
    return new MyBatisPspEventRepository(mapper);
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
  PspWebhookService pspWebhookService(PspEventRepository events) {
    return new PspWebhookService(events);
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
  LedgerClient ledgerClient(PaymentApi paymentApi) {
    return new LedgerHttpClient(paymentApi);
  }

  @Bean
  OrderModificationService orderModificationService(
      OrderRepository repository, AccountingRequestQueue queue) {
    return new OrderModificationService(repository, queue);
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
