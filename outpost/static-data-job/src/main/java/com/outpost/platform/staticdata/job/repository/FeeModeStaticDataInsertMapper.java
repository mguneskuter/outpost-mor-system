package com.outpost.platform.staticdata.job.repository;

import com.outpost.merchant.repository.FeeModeRecord;
import com.outpost.persistence.RegisteredMapper;

/** Job-only insert mapper for fee modes. */
@RegisteredMapper
public interface FeeModeStaticDataInsertMapper {
  /** Inserts one record. */
  int insert(FeeModeRecord record);
}
