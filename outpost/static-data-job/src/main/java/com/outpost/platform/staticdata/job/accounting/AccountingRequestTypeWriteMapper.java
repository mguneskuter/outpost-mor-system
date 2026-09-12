package com.outpost.platform.staticdata.job.accounting;

import com.outpost.accounting.queue.repository.AccountingRequestTypeRecord;
import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;

/** Reads and writes accounting request type rows. */
@RegisteredMapper
public interface AccountingRequestTypeWriteMapper {
  /** Reads all accounting request type rows. */
  List<AccountingRequestTypeRecord> findAll();

  /** Inserts one accounting request type row. */
  int insert(AccountingRequestTypeRecord record);
}
