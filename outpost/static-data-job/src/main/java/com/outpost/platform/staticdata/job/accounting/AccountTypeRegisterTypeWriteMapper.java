package com.outpost.platform.staticdata.job.accounting;

import com.outpost.accounting.repository.AccountTypeRegisterTypeRecord;
import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;

/** Reads and writes account-type and register-type rows. */
@RegisteredMapper
public interface AccountTypeRegisterTypeWriteMapper {
  /** Reads all account-type and register-type rows. */
  List<AccountTypeRegisterTypeRecord> findAll();

  /** Inserts one account-type and register-type row. */
  int insert(AccountTypeRegisterTypeRecord record);
}
