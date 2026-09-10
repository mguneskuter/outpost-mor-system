package com.outpost.platform.staticdata.job.repository;

import com.outpost.common.iso.repository.CountrySubdivisionRecord;
import com.outpost.framework.persistence.RegisteredMapper;

/** Job-only insert mapper for country subdivisions. */
@RegisteredMapper
public interface CountrySubdivisionWriteMapper {
  /** Inserts one record. */
  int insert(CountrySubdivisionRecord record);
}
