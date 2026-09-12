package com.outpost.gateway.configuration;

import com.outpost.gateway.psp.service.PspWebhookService;
import com.outpost.gateway.security.AesGcmSecretAdapter;
import com.outpost.gateway.security.MerchantAuthenticationFilter;
import com.outpost.gateway.security.repository.MerchantApiKeyRepository;
import com.outpost.gateway.security.repository.mybatis.MerchantApiKeyRepositoryMapper;
import com.outpost.gateway.security.repository.mybatis.MybatisMerchantApiKeyRepository;
import com.outpost.gateway.tax.repository.mybatis.MybatisTaxRateRepository;
import com.outpost.gateway.tax.repository.mybatis.TaxRateProviderRepositoryMapper;
import com.outpost.integration.psp.simulator.repository.PspConfigurationRepository;
import com.outpost.integration.psp.simulator.repository.mybatis.MybatisPspConfigurationRepository;
import com.outpost.integration.psp.simulator.repository.mybatis.PspConfigurationRepositoryMapper;
import com.outpost.payment.repository.PspEventQueue;
import com.outpost.payment.repository.mybatis.MybatisPspEventQueue;
import com.outpost.payment.repository.mybatis.PspEventQueueMapper;
import com.outpost.tax.provider.TaxRateProvider;
import com.outpost.tax.provider.cached.CachedTaxRateProvider;
import com.outpost.tax.repository.TaxRateRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
}
