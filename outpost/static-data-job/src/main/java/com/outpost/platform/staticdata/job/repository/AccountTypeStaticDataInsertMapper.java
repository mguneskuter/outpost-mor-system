package com.outpost.platform.staticdata.job.repository;

import com.outpost.account.repository.AccountTypeRecord;
import com.outpost.persistence.RegisteredMapper;

/** Job-only insert mapper for account types. */
@RegisteredMapper
public interface AccountTypeStaticDataInsertMapper {
  /** Inserts one record. */
  int insert(AccountTypeRecord record);
}
