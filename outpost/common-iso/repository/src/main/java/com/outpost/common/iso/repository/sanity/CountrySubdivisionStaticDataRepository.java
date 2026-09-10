package com.outpost.common.iso.repository.sanity;

import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.common.iso.repository.CountrySubdivisionRecord;
import com.outpost.platform.staticdata.StaticDataRepository;
import java.util.List;

/** Typed repository for country subdivisions. */
final class CountrySubdivisionStaticDataRepository
    implements StaticDataRepository<
        CountrySubdivisions, CountrySubdivision, CountrySubdivisionRecord> {
  private final CountrySubdivisionStaticDataMapper mapper;

  /** Creates a typed repository. */
  CountrySubdivisionStaticDataRepository(CountrySubdivisionStaticDataMapper mapper) {
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
    return constant.getValue();
  }

  @Override
  public CountrySubdivisionRecord toDatabaseRecord(CountrySubdivision value) {
    return new CountrySubdivisionRecord(
        value.getCountrySubdivisionId(),
        value.getCountry().getCountryId(),
        value.getCode(),
        value.getName());
  }

  @Override
  public CountrySubdivision toDomainValue(CountrySubdivisionRecord record) {
    return CountrySubdivisions.fromCode(record.code())
        .filter(
            value ->
                value.getCountrySubdivisionId() == record.countrySubdivisionId()
                    && value.getCountry().getCountryId() == record.countryId()
                    && value.getName().equals(record.name()))
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
