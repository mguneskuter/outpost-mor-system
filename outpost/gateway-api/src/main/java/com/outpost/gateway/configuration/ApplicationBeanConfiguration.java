package com.outpost.gateway.configuration;

import com.outpost.gateway.tax.repository.mybatis.MybatisTaxRateRepository;
import com.outpost.gateway.tax.repository.mybatis.TaxRateProviderRepositoryMapper;
import com.outpost.tax.provider.TaxRateProvider;
import com.outpost.tax.provider.cached.CachedTaxRateProvider;
import com.outpost.tax.repository.TaxRateRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Application bean definitions. */
@Configuration(proxyBeanMethods = false)
public class ApplicationBeanConfiguration {

  @Bean
  TaxRateRepository taxRateRepository(TaxRateProviderRepositoryMapper mapper) {
    return new MybatisTaxRateRepository(mapper);
  }

  /** Creates the tax-rate provider. */
  @Bean
  TaxRateProvider taxRateProvider(TaxRateRepository repository) {
    return new CachedTaxRateProvider(repository);
  }
}
