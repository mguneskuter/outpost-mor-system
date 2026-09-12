package com.outpost.platform.staticdata.job.payment;

import com.outpost.framework.persistence.RegisteredMapper;
import com.outpost.payment.repository.PspEventCodeRecord;
import java.util.List;

/** Writes PSP event codes to persistence. */
@RegisteredMapper
public interface PspEventCodeWriteMapper {
  /** Returns all PSP event codes. */
  List<PspEventCodeRecord> findAll();

  /** Inserts one PSP event code. */
  int insert(PspEventCodeRecord record);
}
