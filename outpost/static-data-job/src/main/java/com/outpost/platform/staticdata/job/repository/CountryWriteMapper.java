package com.outpost.platform.staticdata.job.repository;

import com.outpost.common.iso.repository.CountryRecord;
import com.outpost.framework.persistence.RegisteredMapper;

/** Job-only insert mapper for countries. */
@RegisteredMapper
public interface CountryWriteMapper {
  /** Inserts one record. */
  int insert(CountryRecord record);
}
