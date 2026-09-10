package com.outpost.platform.staticdata.job.accounting;

import com.outpost.accounting.repository.TransactionEventTypeRecord;
import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;

/** Reads and writes transaction-event-type rows. */
@RegisteredMapper
public interface TransactionEventTypeWriteMapper {
  /** Reads all transaction-event-type rows. */
  List<TransactionEventTypeRecord> findAll();

  /** Inserts one transaction-event-type row. */
  int insert(TransactionEventTypeRecord record);
}
