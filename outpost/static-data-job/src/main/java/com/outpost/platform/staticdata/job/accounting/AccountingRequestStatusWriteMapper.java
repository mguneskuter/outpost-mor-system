package com.outpost.platform.staticdata.job.accounting;

import com.outpost.accounting.queue.repository.AccountingRequestStatusRecord;
import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;

/** Reads and writes accounting request status rows. */
@RegisteredMapper
public interface AccountingRequestStatusWriteMapper {
  /** Reads all accounting request status rows. */
  List<AccountingRequestStatusRecord> findAll();

  /** Inserts one accounting request status row. */
  int insert(AccountingRequestStatusRecord record);
}
