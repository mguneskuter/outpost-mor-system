package com.outpost.payment.repository.sanity;

import com.outpost.framework.persistence.RegisteredMapper;
import com.outpost.payment.repository.PspEventStatusRecord;
import java.util.List;
import org.apache.ibatis.annotations.Select;

/** Reads PSP event statuses from persistence. */
@RegisteredMapper
public interface PspEventStatusStaticDataMapper {
  /** Returns all PSP event statuses in id order. */
  @Select("SELECT psp_event_status_id, code FROM psp_event_status ORDER BY psp_event_status_id")
  List<PspEventStatusRecord> findAll();
}
