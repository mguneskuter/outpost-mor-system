package com.outpost.platform.staticdata.job.accounting;

import com.outpost.accounting.queue.repository.AccountingRequestResultRecord;
import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;

/** Reads and writes accounting request result rows. */
@RegisteredMapper
public interface AccountingRequestResultWriteMapper {
  /** Reads all accounting request result rows. */
  List<AccountingRequestResultRecord> findAll();

  /** Inserts one accounting request result row. */
  int insert(AccountingRequestResultRecord record);
}
