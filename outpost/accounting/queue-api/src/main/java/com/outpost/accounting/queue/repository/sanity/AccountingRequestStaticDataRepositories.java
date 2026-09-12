package com.outpost.accounting.queue.repository.sanity;

import com.outpost.accounting.queue.AccountingRequestResults;
import com.outpost.accounting.queue.AccountingRequestResults.AccountingRequestResult;
import com.outpost.accounting.queue.AccountingRequestStatuses;
import com.outpost.accounting.queue.AccountingRequestStatuses.AccountingRequestStatus;
import com.outpost.accounting.queue.AccountingRequestTypes;
import com.outpost.accounting.queue.AccountingRequestTypes.AccountingRequestType;
import com.outpost.accounting.queue.repository.AccountingRequestResultRecord;
import com.outpost.accounting.queue.repository.AccountingRequestStatusRecord;
import com.outpost.accounting.queue.repository.AccountingRequestTypeRecord;
import com.outpost.platform.staticdata.StaticDataRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the static-data repositories owned by the request queue. */
@Configuration(proxyBeanMethods = false)
public class AccountingRequestStaticDataRepositories {
  @Bean
  StaticDataRepository<AccountingRequestTypes, AccountingRequestType, AccountingRequestTypeRecord>
      accountingRequestTypeRepository(AccountingRequestTypeStaticDataMapper mapper) {
    return new AccountingRequestTypeStaticDataRepository(mapper);
  }

  @Bean
  StaticDataRepository<
          AccountingRequestStatuses, AccountingRequestStatus, AccountingRequestStatusRecord>
      accountingRequestStatusRepository(AccountingRequestStatusStaticDataMapper mapper) {
    return new AccountingRequestStatusStaticDataRepository(mapper);
  }

  @Bean
  StaticDataRepository<
          AccountingRequestResults, AccountingRequestResult, AccountingRequestResultRecord>
      accountingRequestResultRepository(AccountingRequestResultStaticDataMapper mapper) {
    return new AccountingRequestResultStaticDataRepository(mapper);
  }
}
