package com.outpost.merchant.repository;

import com.outpost.merchant.FeeModes;
import com.outpost.merchant.FeeModes.FeeMode;
import com.outpost.platform.staticdata.StaticDataRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Public repository factory for merchant configuration static data. */
@Configuration(proxyBeanMethods = false)
public class MerchantConfigurationStaticDataRepositories {

  /** Creates the fee-mode repository. */
  @Bean
  public StaticDataRepository<FeeModes, FeeMode, FeeModeRecord> feeModeRepository(
      FeeModeStaticDataReadMapper mapper) {
    return new FeeModeStaticDataRepository(mapper);
  }
}
