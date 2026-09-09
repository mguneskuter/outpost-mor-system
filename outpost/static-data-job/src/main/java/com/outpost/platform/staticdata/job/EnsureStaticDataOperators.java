package com.outpost.platform.staticdata.job;

import com.outpost.account.AccountTypes;
import com.outpost.account.AccountTypes.AccountType;
import com.outpost.account.configuration.FeeModes;
import com.outpost.account.configuration.FeeModes.FeeMode;
import com.outpost.account.configuration.repository.FeeModeRecord;
import com.outpost.account.repository.AccountTypeRecord;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.common.iso.repository.CountryRecord;
import com.outpost.common.iso.repository.CountrySubdivisionRecord;
import com.outpost.common.iso.repository.CurrencyRecord;
import com.outpost.payment.common.ProductTypes;
import com.outpost.payment.common.ProductTypes.ProductType;
import com.outpost.payment.common.repository.ProductTypeRecord;
import com.outpost.platform.staticdata.StaticDataRepository;
import com.outpost.platform.staticdata.job.repository.AccountTypeStaticDataInsertMapper;
import com.outpost.platform.staticdata.job.repository.CountryStaticDataInsertMapper;
import com.outpost.platform.staticdata.job.repository.CountrySubdivisionStaticDataInsertMapper;
import com.outpost.platform.staticdata.job.repository.CurrencyStaticDataInsertMapper;
import com.outpost.platform.staticdata.job.repository.FeeModeStaticDataInsertMapper;
import com.outpost.platform.staticdata.job.repository.ProductTypeStaticDataInsertMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Supplies the typed operators discovered by {@link EnsureStaticDataJob}. */
@Configuration(proxyBeanMethods = false)
public class EnsureStaticDataOperators {
  @Bean
  EnsureStaticDataOperator<Countries, Country, CountryRecord> countryEnsureOperator(
      StaticDataRepository<Countries, Country, CountryRecord> repository,
      CountryStaticDataInsertMapper mapper) {
    return new EnsureStaticDataOperator<>(repository, mapper::insert);
  }

  @Bean
  EnsureStaticDataOperator<CountrySubdivisions, CountrySubdivision, CountrySubdivisionRecord>
      countrySubdivisionEnsureOperator(
          StaticDataRepository<CountrySubdivisions, CountrySubdivision, CountrySubdivisionRecord>
              repository,
          CountrySubdivisionStaticDataInsertMapper mapper) {
    return new EnsureStaticDataOperator<>(repository, mapper::insert);
  }

  @Bean
  EnsureStaticDataOperator<Currencies, Currency, CurrencyRecord> currencyEnsureOperator(
      StaticDataRepository<Currencies, Currency, CurrencyRecord> repository,
      CurrencyStaticDataInsertMapper mapper) {
    return new EnsureStaticDataOperator<>(repository, mapper::insert);
  }

  @Bean
  EnsureStaticDataOperator<ProductTypes, ProductType, ProductTypeRecord> productTypeEnsureOperator(
      StaticDataRepository<ProductTypes, ProductType, ProductTypeRecord> repository,
      ProductTypeStaticDataInsertMapper mapper) {
    return new EnsureStaticDataOperator<>(repository, mapper::insert);
  }

  @Bean
  EnsureStaticDataOperator<AccountTypes, AccountType, AccountTypeRecord> accountTypeEnsureOperator(
      StaticDataRepository<AccountTypes, AccountType, AccountTypeRecord> repository,
      AccountTypeStaticDataInsertMapper mapper) {
    return new EnsureStaticDataOperator<>(repository, mapper::insert);
  }

  @Bean
  EnsureStaticDataOperator<FeeModes, FeeMode, FeeModeRecord> feeModeEnsureOperator(
      StaticDataRepository<FeeModes, FeeMode, FeeModeRecord> repository,
      FeeModeStaticDataInsertMapper mapper) {
    return new EnsureStaticDataOperator<>(repository, mapper::insert);
  }
}
