package com.outpost.payment.common.repository;

import com.outpost.payment.common.ProductTypes;
import com.outpost.payment.common.ProductTypes.ProductType;
import com.outpost.platform.staticdata.StaticDataRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Public repository factory for common payment static data. */
@Configuration(proxyBeanMethods = false)
public class CommonPaymentStaticDataRepositories {

  /** Creates the product-type repository. */
  @Bean
  public StaticDataRepository<ProductTypes, ProductType, ProductTypeRecord> productTypeRepository(
      ProductTypeStaticDataReadMapper mapper) {
    return new ProductTypeStaticDataRepository(mapper);
  }
}
