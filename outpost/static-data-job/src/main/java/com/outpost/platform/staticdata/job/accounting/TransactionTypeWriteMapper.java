package com.outpost.platform.staticdata.job.accounting;

import com.outpost.accounting.repository.TransactionTypeRecord;
import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;

/** Reads and writes transaction-type rows. */
@RegisteredMapper
public interface TransactionTypeWriteMapper {
  /** Reads all transaction-type rows. */
  List<TransactionTypeRecord> findAll();

  /** Inserts one transaction-type row. */
  int insert(TransactionTypeRecord record);
}
