package com.outpost.account.configuration.repository;

import com.outpost.account.configuration.FeeModes;
import com.outpost.account.configuration.FeeModes.FeeMode;
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
