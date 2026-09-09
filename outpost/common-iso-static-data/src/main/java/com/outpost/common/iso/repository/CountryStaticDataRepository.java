package com.outpost.common.iso.repository;

import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Countries.Country;
import com.outpost.platform.staticdata.StaticDataRepository;
import java.util.List;

/** Typed repository for countries. */
final class CountryStaticDataRepository
    implements StaticDataRepository<Countries, Country, CountryRecord> {
  private final CountryStaticDataReadMapper mapper;

  /** Creates a typed repository. */
  CountryStaticDataRepository(CountryStaticDataReadMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Class<Countries> staticDataEnum() {
    return Countries.class;
  }

  @Override
  public String table() {
    return "country";
  }

  @Override
  public Country enumValue(Countries constant) {
    return constant.value();
  }

  @Override
  public CountryRecord toDatabaseRecord(Country value) {
    return new CountryRecord(value.countryId(), value.isoCode(), value.name());
  }

  @Override
  public Country toDomainValue(CountryRecord record) {
    return Countries.fromIsoCode(record.isoCode())
        .filter(
            value -> value.countryId() == record.countryId() && value.name().equals(record.name()))
        .orElseThrow(
            () ->
                new IllegalArgumentException("Country record does not match the enum: " + record));
  }

  @Override
  public long id(CountryRecord record) {
    return record.countryId();
  }

  @Override
  public List<CountryRecord> findAll() {
    return mapper.findAll();
  }
}
