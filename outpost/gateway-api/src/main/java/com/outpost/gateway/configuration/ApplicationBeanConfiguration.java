package com.outpost.gateway.configuration;

import com.outpost.accounting.queue.AccountingRequestQueue;
import com.outpost.gateway.order.client.LedgerClient;
import com.outpost.gateway.order.client.ledger.LedgerHttpClient;
import com.outpost.gateway.order.repository.OrderRepository;
import com.outpost.gateway.order.repository.mybatis.MyBatisOrderRepository;
import com.outpost.gateway.order.repository.mybatis.OrderMapper;
import com.outpost.gateway.order.service.OrderModificationService;
import com.outpost.gateway.order.service.OrderService;
import com.outpost.gateway.paymentmethod.repository.PaymentMethodRepository;
import com.outpost.gateway.paymentmethod.repository.mybatis.MyBatisPaymentMethodRepository;
import com.outpost.gateway.paymentmethod.repository.mybatis.PaymentMethodMapper;
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
import com.outpost.gateway.security.repository.mybatis.MerchantApiKeyRepositoryMapper;
import com.outpost.gateway.security.repository.mybatis.MybatisMerchantApiKeyRepository;
import com.outpost.gateway.tax.repository.mybatis.MybatisTaxRateRepository;
import com.outpost.gateway.tax.repository.mybatis.TaxRateProviderRepositoryMapper;
import com.outpost.integration.psp.service.PspClient;
import com.outpost.integration.psp.simulator.SimulatorPspClient;
import com.outpost.integration.psp.simulator.repository.PspConfigurationRepository;
import com.outpost.integration.psp.simulator.repository.mybatis.MybatisPspConfigurationRepository;
import com.outpost.integration.psp.simulator.repository.mybatis.PspConfigurationRepositoryMapper;
import com.outpost.payment.repository.PspEventQueue;
import com.outpost.payment.repository.mybatis.MybatisPspEventQueue;
import com.outpost.payment.repository.mybatis.PspEventQueueMapper;
import com.outpost.tax.provider.TaxRateProvider;
import com.outpost.tax.provider.cached.CachedTaxRateProvider;
import com.outpost.tax.repository.TaxRateRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.ObjectMapper;

/** Application bean definitions. */
@Configuration(proxyBeanMethods = false)
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
  MerchantApiKeyRepository merchantApiKeyRepository(MerchantApiKeyRepositoryMapper mapper) {
    return new MybatisMerchantApiKeyRepository(mapper);
  }

  @Bean
  TaxRateRepository taxRateRepository(TaxRateProviderRepositoryMapper mapper) {
    return new MybatisTaxRateRepository(mapper);
  }

  @Bean
  PspConfigurationRepository pspConfigurationRepository(PspConfigurationRepositoryMapper mapper) {
    return new MybatisPspConfigurationRepository(mapper, 10_000, 30_000);
  }

  @Bean
  PspEventQueue pspEventQueue(PspEventQueueMapper mapper) {
    return new MybatisPspEventQueue(mapper);
  }

  @Bean
  PspWebhookService pspWebhookService(
      PspConfigurationRepository configurations, PspEventQueue events, ObjectMapper objectMapper) {
    return new PspWebhookService(configurations, events, objectMapper);
  }

  /** Creates the tax-rate provider. */
  @Bean
  TaxRateProvider taxRateProvider(TaxRateRepository repository) {
    return new CachedTaxRateProvider(repository);
  }

  @Bean
  Clock gatewayClock() {
    return Clock.systemUTC();
  }

  @Bean
  DataSource orderPhaseOwnershipDataSource(
      @Value("${spring.datasource.url}") String url,
      @Value("${spring.datasource.username}") String username,
      @Value("${spring.datasource.password}") String password,
      @Value("${outpost.gateway.order.phase-ownership.tcp-keepalives-idle:10}")
          int tcpKeepalivesIdle,
      @Value("${outpost.gateway.order.phase-ownership.tcp-keepalives-interval:5}")
          int tcpKeepalivesInterval,
      @Value("${outpost.gateway.order.phase-ownership.tcp-keepalives-count:3}")
          int tcpKeepalivesCount) {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(
        withPhaseOwnershipOptions(
            url, tcpKeepalivesIdle, tcpKeepalivesInterval, tcpKeepalivesCount));
    dataSource.setUsername(username);
    dataSource.setPassword(password);
    return dataSource;
  }

  private static String withPhaseOwnershipOptions(
      String url, int tcpKeepalivesIdle, int tcpKeepalivesInterval, int tcpKeepalivesCount) {
    String options =
        "-c tcp_keepalives_idle="
            + tcpKeepalivesIdle
            + " -c tcp_keepalives_interval="
            + tcpKeepalivesInterval
            + " -c tcp_keepalives_count="
            + tcpKeepalivesCount;
    String separator = url.contains("?") ? "&" : "?";
    return url
        + separator
        + "tcpKeepAlive=true&ApplicationName=outpost-gateway-order-phase&options="
        + URLEncoder.encode(options, StandardCharsets.UTF_8);
  }

  @Bean
  OrderRepository orderRepository(
      OrderMapper mapper, @Qualifier("orderPhaseOwnershipDataSource") DataSource dataSource) {
    return new MyBatisOrderRepository(mapper, dataSource);
  }

  @Bean
  PspClient pspClient(PspConfigurationRepository configurations) {
    return new SimulatorPspClient(configurations);
  }

  @Bean
  LedgerClient ledgerClient(
      ObjectMapper objectMapper,
      @Value("${outpost.gateway.ledger.base-url:http://localhost:8081}") String baseUrl,
      @Value("${outpost.gateway.ledger.hmac-secret:gateway-key}") String hmacSecret,
      @Value("${outpost.gateway.ledger.connect-timeout:PT1S}") Duration connectTimeout,
      @Value("${outpost.gateway.ledger.read-timeout:PT5S}") Duration readTimeout) {
    return new LedgerHttpClient(baseUrl, hmacSecret, connectTimeout, readTimeout, objectMapper);
  }

  @Bean
  OrderService orderService(
      OrderRepository repository,
      LedgerClient ledgerClient,
      PspClient pspClient,
      TaxRateProvider taxRateProvider,
      Clock clock) {
    return new OrderService(repository, ledgerClient, pspClient, taxRateProvider, clock);
  }

  @Bean
  OrderModificationService orderModificationService(
      OrderRepository repository, AccountingRequestQueue queue) {
    return new OrderModificationService(repository, queue);
  }

  @Bean
  LedgerReportClient ledgerReportClient(
      ObjectMapper objectMapper,
      @Value("${outpost.gateway.ledger.base-url:http://localhost:8081}") String baseUrl,
      @Value("${outpost.gateway.ledger.hmac-secret:gateway-key}") String hmacSecret,
      @Value("${outpost.gateway.ledger.connect-timeout:PT1S}") Duration connectTimeout,
      @Value("${outpost.gateway.ledger.read-timeout:PT5S}") Duration readTimeout) {
    return new LedgerReportHttpClient(
        baseUrl, hmacSecret, connectTimeout, readTimeout, objectMapper);
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
