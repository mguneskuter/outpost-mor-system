package com.outpost.accounting.repository.sanity;

import com.outpost.accounting.JournalEntryTypes;
import com.outpost.accounting.JournalEntryTypes.JournalEntryType;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.RegisterTypes.RegisterType;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionEventTypes.TransactionEventType;
import com.outpost.accounting.TransactionTypes;
import com.outpost.accounting.TransactionTypes.TransactionType;
import com.outpost.accounting.repository.JournalEntryTypeRecord;
import com.outpost.accounting.repository.RegisterTypeRecord;
import com.outpost.accounting.repository.TransactionEventTypeRecord;
import com.outpost.accounting.repository.TransactionTypeRecord;
import com.outpost.platform.staticdata.StaticDataRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers accounting static-data repositories. */
@Configuration(proxyBeanMethods = false)
public class AccountingStaticDataRepositories {
  @Bean
  StaticDataRepository<RegisterTypes, RegisterType, RegisterTypeRecord> registerTypeRepository(
      RegisterTypeStaticDataMapper mapper) {
    return new RegisterTypeStaticDataRepository(mapper);
  }

  @Bean
  StaticDataRepository<TransactionTypes, TransactionType, TransactionTypeRecord>
      transactionTypeRepository(TransactionTypeStaticDataMapper mapper) {
    return new TransactionTypeStaticDataRepository(mapper);
  }

  @Bean
  StaticDataRepository<TransactionEventTypes, TransactionEventType, TransactionEventTypeRecord>
      transactionEventTypeRepository(TransactionEventTypeStaticDataMapper mapper) {
    return new TransactionEventTypeStaticDataRepository(mapper);
  }

  @Bean
  StaticDataRepository<JournalEntryTypes, JournalEntryType, JournalEntryTypeRecord>
      journalEntryTypeRepository(JournalEntryTypeStaticDataMapper mapper) {
    return new JournalEntryTypeStaticDataRepository(mapper);
  }
}
