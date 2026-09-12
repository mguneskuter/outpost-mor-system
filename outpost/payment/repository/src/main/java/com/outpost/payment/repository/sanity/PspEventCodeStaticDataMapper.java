package com.outpost.payment.repository.sanity;

import com.outpost.framework.persistence.RegisteredMapper;
import com.outpost.payment.repository.PspEventCodeRecord;
import java.util.List;
import org.apache.ibatis.annotations.Select;

/** Reads PSP event codes from persistence. */
@RegisteredMapper
public interface PspEventCodeStaticDataMapper {
  /** Returns all PSP event codes in id order. */
  @Select("SELECT psp_event_code_id, code FROM psp_event_code ORDER BY psp_event_code_id")
  List<PspEventCodeRecord> findAll();
}
