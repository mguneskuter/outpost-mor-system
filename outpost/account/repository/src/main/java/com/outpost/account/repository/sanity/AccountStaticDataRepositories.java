package com.outpost.account.repository.sanity;

import com.outpost.account.AccountTypes;
import com.outpost.account.AccountTypes.AccountType;
import com.outpost.account.repository.AccountTypeRecord;
import com.outpost.platform.staticdata.StaticDataRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Repositories for the account module's static data. */
@Configuration(proxyBeanMethods = false)
public class AccountStaticDataRepositories {

  /** Creates the account-type repository. */
  @Bean
  public StaticDataRepository<AccountTypes, AccountType, AccountTypeRecord> accountTypeRepository(
      AccountTypeStaticDataMapper mapper) {
    return new AccountTypeStaticDataRepository(mapper);
  }
}
