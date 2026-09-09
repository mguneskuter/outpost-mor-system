package com.outpost.common.iso.repository;

import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.platform.staticdata.StaticDataRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Public repository factory for common ISO static data. */
@Configuration(proxyBeanMethods = false)
public class CommonIsoStaticDataRepositories {

  /** Creates the country repository. */
  @Bean
  public StaticDataRepository<Countries, Country, CountryRecord> countryRepository(
      CountryStaticDataReadMapper mapper) {
    return new CountryStaticDataRepository(mapper);
  }

  /** Creates the country-subdivision repository. */
  @Bean
  public StaticDataRepository<CountrySubdivisions, CountrySubdivision, CountrySubdivisionRecord>
      countrySubdivisionRepository(CountrySubdivisionStaticDataReadMapper mapper) {
    return new CountrySubdivisionStaticDataRepository(mapper);
  }

  /** Creates the currency repository. */
  @Bean
  public StaticDataRepository<Currencies, Currency, CurrencyRecord> currencyRepository(
      CurrencyStaticDataReadMapper mapper) {
    return new CurrencyStaticDataRepository(mapper);
  }
}
