package com.outpost.platform.staticdata.job.repository;

import com.outpost.common.iso.repository.CountryRecord;
import com.outpost.framework.persistence.RegisteredMapper;

/** Job-only insert mapper for countries. */
@RegisteredMapper
public interface CountryStaticDataInsertMapper {
  /** Inserts one record. */
  int insert(CountryRecord record);
}
