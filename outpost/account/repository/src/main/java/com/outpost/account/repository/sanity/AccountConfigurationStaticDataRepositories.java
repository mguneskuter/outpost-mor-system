package com.outpost.account.repository.sanity;

import com.outpost.account.AccountTypes;
import com.outpost.account.AccountTypes.AccountType;
import com.outpost.account.repository.AccountTypeRecord;
import com.outpost.platform.staticdata.StaticDataRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Public repository factory for account configuration static data. */
@Configuration(proxyBeanMethods = false)
public class AccountConfigurationStaticDataRepositories {

  /** Creates the account-type repository. */
  @Bean
  public StaticDataRepository<AccountTypes, AccountType, AccountTypeRecord> accountTypeRepository(
      AccountTypeStaticDataMapper mapper) {
    return new AccountTypeStaticDataRepository(mapper);
  }
}
