package com.outpost.platform.staticdata.job;

import com.outpost.account.AccountTypes;
import com.outpost.account.AccountTypes.AccountType;
import com.outpost.account.configuration.FeeModes;
import com.outpost.account.configuration.FeeModes.FeeMode;
import com.outpost.account.configuration.repository.FeeModeRecord;
import com.outpost.account.repository.AccountTypeRecord;
import com.outpost.accounting.AccountTypeRegisterTypes;
import com.outpost.accounting.AccountTypeRegisterTypes.AccountTypeRegisterType;
import com.outpost.accounting.JournalEntryTypes;
import com.outpost.accounting.JournalEntryTypes.JournalEntryType;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.RegisterTypes.RegisterType;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionEventTypes.TransactionEventType;
import com.outpost.accounting.TransactionTypes;
import com.outpost.accounting.TransactionTypes.TransactionType;
import com.outpost.accounting.repository.AccountTypeRegisterTypeRecord;
import com.outpost.accounting.repository.JournalEntryTypeRecord;
import com.outpost.accounting.repository.RegisterTypeRecord;
import com.outpost.accounting.repository.TransactionEventTypeRecord;
import com.outpost.accounting.repository.TransactionTypeRecord;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.common.iso.repository.CountryRecord;
import com.outpost.common.iso.repository.CountrySubdivisionRecord;
import com.outpost.common.iso.repository.CurrencyRecord;
import com.outpost.payment.PspEventCodes;
import com.outpost.payment.PspEventCodes.PspEventCode;
import com.outpost.payment.PspEventResults;
import com.outpost.payment.PspEventResults.PspEventResult;
import com.outpost.payment.PspEventStatuses;
import com.outpost.payment.PspEventStatuses.PspEventStatus;
import com.outpost.payment.common.ProductTypes;
import com.outpost.payment.common.ProductTypes.ProductType;
import com.outpost.payment.common.repository.ProductTypeRecord;
import com.outpost.payment.repository.PspEventCodeRecord;
import com.outpost.payment.repository.PspEventResultRecord;
import com.outpost.payment.repository.PspEventStatusRecord;
import com.outpost.platform.staticdata.StaticDataRepository;
import com.outpost.platform.staticdata.job.accounting.AccountTypeRegisterTypeWriteMapper;
import com.outpost.platform.staticdata.job.accounting.JournalEntryTypeWriteMapper;
import com.outpost.platform.staticdata.job.accounting.RegisterTypeWriteMapper;
import com.outpost.platform.staticdata.job.accounting.TransactionEventTypeWriteMapper;
import com.outpost.platform.staticdata.job.accounting.TransactionTypeWriteMapper;
import com.outpost.platform.staticdata.job.payment.PspEventCodeWriteMapper;
import com.outpost.platform.staticdata.job.payment.PspEventResultWriteMapper;
import com.outpost.platform.staticdata.job.payment.PspEventStatusWriteMapper;
import com.outpost.platform.staticdata.job.repository.AccountTypeWriteMapper;
import com.outpost.platform.staticdata.job.repository.CountrySubdivisionWriteMapper;
import com.outpost.platform.staticdata.job.repository.CountryWriteMapper;
import com.outpost.platform.staticdata.job.repository.CurrencyWriteMapper;
import com.outpost.platform.staticdata.job.repository.FeeModeWriteMapper;
import com.outpost.platform.staticdata.job.repository.ProductTypeWriteMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Supplies the typed operators discovered by {@link EnsureStaticDataJob}. */
@Configuration(proxyBeanMethods = false)
public class EnsureStaticDataOperators {
  @Bean
  EnsureStaticDataOperator<PspEventCodes, PspEventCode, PspEventCodeRecord>
      pspEventCodeEnsureOperator(
          StaticDataRepository<PspEventCodes, PspEventCode, PspEventCodeRecord> repository,
          PspEventCodeWriteMapper mapper) {
    return new EnsureStaticDataOperator<>(repository, mapper::insert);
  }

  @Bean
  EnsureStaticDataOperator<PspEventStatuses, PspEventStatus, PspEventStatusRecord>
      pspEventStatusEnsureOperator(
          StaticDataRepository<PspEventStatuses, PspEventStatus, PspEventStatusRecord> repository,
          PspEventStatusWriteMapper mapper) {
    return new EnsureStaticDataOperator<>(repository, mapper::insert);
  }

  @Bean
  EnsureStaticDataOperator<PspEventResults, PspEventResult, PspEventResultRecord>
      pspEventResultEnsureOperator(
          StaticDataRepository<PspEventResults, PspEventResult, PspEventResultRecord> repository,
          PspEventResultWriteMapper mapper) {
    return new EnsureStaticDataOperator<>(repository, mapper::insert);
  }

  @Bean
  EnsureStaticDataOperator<Countries, Country, CountryRecord> countryEnsureOperator(
      StaticDataRepository<Countries, Country, CountryRecord> repository,
      CountryWriteMapper mapper) {
    return new EnsureStaticDataOperator<>(repository, mapper::insert);
  }

  @Bean
  EnsureStaticDataOperator<CountrySubdivisions, CountrySubdivision, CountrySubdivisionRecord>
      countrySubdivisionEnsureOperator(
          StaticDataRepository<CountrySubdivisions, CountrySubdivision, CountrySubdivisionRecord>
              repository,
          CountrySubdivisionWriteMapper mapper) {
    return new EnsureStaticDataOperator<>(repository, mapper::insert);
  }

  @Bean
  EnsureStaticDataOperator<Currencies, Currency, CurrencyRecord> currencyEnsureOperator(
      StaticDataRepository<Currencies, Currency, CurrencyRecord> repository,
      CurrencyWriteMapper mapper) {
    return new EnsureStaticDataOperator<>(repository, mapper::insert);
  }

  @Bean
  EnsureStaticDataOperator<ProductTypes, ProductType, ProductTypeRecord> productTypeEnsureOperator(
      StaticDataRepository<ProductTypes, ProductType, ProductTypeRecord> repository,
      ProductTypeWriteMapper mapper) {
    return new EnsureStaticDataOperator<>(repository, mapper::insert);
  }

  @Bean
  EnsureStaticDataOperator<AccountTypes, AccountType, AccountTypeRecord> accountTypeEnsureOperator(
      StaticDataRepository<AccountTypes, AccountType, AccountTypeRecord> repository,
      AccountTypeWriteMapper mapper) {
    return new EnsureStaticDataOperator<>(repository, mapper::insert);
  }

  @Bean
  EnsureStaticDataOperator<FeeModes, FeeMode, FeeModeRecord> feeModeEnsureOperator(
      StaticDataRepository<FeeModes, FeeMode, FeeModeRecord> repository,
      FeeModeWriteMapper mapper) {
    return new EnsureStaticDataOperator<>(repository, mapper::insert);
  }

  @Bean
  EnsureStaticDataOperator<RegisterTypes, RegisterType, RegisterTypeRecord>
      registerTypeEnsureOperator(
          StaticDataRepository<RegisterTypes, RegisterType, RegisterTypeRecord> repository,
          RegisterTypeWriteMapper mapper) {
    return new EnsureStaticDataOperator<>(repository, mapper::insert);
  }

  @Bean
  EnsureStaticDataOperator<
          AccountTypeRegisterTypes, AccountTypeRegisterType, AccountTypeRegisterTypeRecord>
      accountTypeRegisterTypeEnsureOperator(
          StaticDataRepository<
                  AccountTypeRegisterTypes, AccountTypeRegisterType, AccountTypeRegisterTypeRecord>
              repository,
          AccountTypeRegisterTypeWriteMapper mapper) {
    return new EnsureStaticDataOperator<>(repository, mapper::insert);
  }

  @Bean
  EnsureStaticDataOperator<TransactionTypes, TransactionType, TransactionTypeRecord>
      transactionTypeEnsureOperator(
          StaticDataRepository<TransactionTypes, TransactionType, TransactionTypeRecord> repository,
          TransactionTypeWriteMapper mapper) {
    return new EnsureStaticDataOperator<>(repository, mapper::insert);
  }

  @Bean
  EnsureStaticDataOperator<TransactionEventTypes, TransactionEventType, TransactionEventTypeRecord>
      transactionEventTypeEnsureOperator(
          StaticDataRepository<
                  TransactionEventTypes, TransactionEventType, TransactionEventTypeRecord>
              repository,
          TransactionEventTypeWriteMapper mapper) {
    return new EnsureStaticDataOperator<>(repository, mapper::insert);
  }

  @Bean
  EnsureStaticDataOperator<JournalEntryTypes, JournalEntryType, JournalEntryTypeRecord>
      journalEntryTypeEnsureOperator(
          StaticDataRepository<JournalEntryTypes, JournalEntryType, JournalEntryTypeRecord>
              repository,
          JournalEntryTypeWriteMapper mapper) {
    return new EnsureStaticDataOperator<>(repository, mapper::insert);
  }
}
