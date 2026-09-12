package com.outpost.platform.staticdata.job.payment;

import com.outpost.framework.persistence.RegisteredMapper;
import com.outpost.payment.repository.PspEventStatusRecord;
import java.util.List;

/** Writes PSP event statuses to persistence. */
@RegisteredMapper
public interface PspEventStatusWriteMapper {
  /** Returns all PSP event statuses. */
  List<PspEventStatusRecord> findAll();

  /** Inserts one PSP event status. */
  int insert(PspEventStatusRecord record);
}
