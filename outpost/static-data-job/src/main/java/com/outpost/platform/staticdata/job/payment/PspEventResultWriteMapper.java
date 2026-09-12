package com.outpost.platform.staticdata.job.payment;

import com.outpost.framework.persistence.RegisteredMapper;
import com.outpost.payment.repository.PspEventResultRecord;
import java.util.List;

/** Writes PSP event results to persistence. */
@RegisteredMapper
public interface PspEventResultWriteMapper {
  /** Returns all PSP event results. */
  List<PspEventResultRecord> findAll();

  /** Inserts one PSP event result. */
  int insert(PspEventResultRecord record);
}
