package com.outpost.payment.common.repository.sanity;

import com.outpost.payment.common.ProductTypes;
import com.outpost.payment.common.ProductTypes.ProductType;
import com.outpost.payment.common.repository.ProductTypeRecord;
import com.outpost.platform.staticdata.StaticDataRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Public repository factory for common payment static data. */
@Configuration(proxyBeanMethods = false)
public class CommonPaymentStaticDataRepositories {

  /** Creates the product-type repository. */
  @Bean
  public StaticDataRepository<ProductTypes, ProductType, ProductTypeRecord> productTypeRepository(
      ProductTypeStaticDataMapper mapper) {
    return new ProductTypeStaticDataRepository(mapper);
  }
}
