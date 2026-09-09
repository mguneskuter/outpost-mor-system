package com.outpost.platform.staticdata.job.repository;

import com.outpost.common.iso.repository.CurrencyRecord;
import com.outpost.persistence.RegisteredMapper;

/** Job-only insert mapper for currencies. */
@RegisteredMapper
public interface CurrencyStaticDataInsertMapper {
  /** Inserts one record. */
  int insert(CurrencyRecord record);
}
