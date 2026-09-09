package com.outpost.common.iso.repository;

import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.platform.staticdata.StaticDataRepository;
import java.util.List;

/** Typed repository for country subdivisions. */
final class CountrySubdivisionStaticDataRepository
    implements StaticDataRepository<
        CountrySubdivisions, CountrySubdivision, CountrySubdivisionRecord> {
  private final CountrySubdivisionStaticDataReadMapper mapper;

  /** Creates a typed repository. */
  CountrySubdivisionStaticDataRepository(CountrySubdivisionStaticDataReadMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Class<CountrySubdivisions> staticDataEnum() {
    return CountrySubdivisions.class;
  }

  @Override
  public String table() {
    return "country_subdivision";
  }

  @Override
  public CountrySubdivision enumValue(CountrySubdivisions constant) {
    return constant.value();
  }

  @Override
  public CountrySubdivisionRecord toDatabaseRecord(CountrySubdivision value) {
    return new CountrySubdivisionRecord(
        value.countrySubdivisionId(), value.country().countryId(), value.code(), value.name());
  }

  @Override
  public CountrySubdivision toDomainValue(CountrySubdivisionRecord record) {
    return CountrySubdivisions.fromCode(record.code())
        .filter(
            value ->
                value.countrySubdivisionId() == record.countrySubdivisionId()
                    && value.country().countryId() == record.countryId()
                    && value.name().equals(record.name()))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Country subdivision record does not match the enum: " + record));
  }

  @Override
  public long id(CountrySubdivisionRecord record) {
    return record.countrySubdivisionId();
  }

  @Override
  public List<CountrySubdivisionRecord> findAll() {
    return mapper.findAll();
  }
}
