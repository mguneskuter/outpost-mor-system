package com.outpost.platform.staticdata.job.repository;

import com.outpost.account.configuration.repository.FeeModeRecord;
import com.outpost.framework.persistence.RegisteredMapper;

/** Job-only insert mapper for fee modes. */
@RegisteredMapper
public interface FeeModeStaticDataInsertMapper {
  /** Inserts one record. */
  int insert(FeeModeRecord record);
}
