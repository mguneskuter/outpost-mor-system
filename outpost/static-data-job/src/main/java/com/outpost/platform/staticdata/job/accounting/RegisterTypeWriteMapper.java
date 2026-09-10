package com.outpost.platform.staticdata.job.accounting;

import com.outpost.accounting.repository.RegisterTypeRecord;
import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;

/** Reads and writes register-type rows. */
@RegisteredMapper
public interface RegisterTypeWriteMapper {
  /** Reads all register-type rows. */
  List<RegisterTypeRecord> findAll();

  /** Inserts one register-type row. */
  int insert(RegisterTypeRecord record);
}
