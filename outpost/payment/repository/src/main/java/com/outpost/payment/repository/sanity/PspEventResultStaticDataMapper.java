package com.outpost.payment.repository.sanity;

import com.outpost.framework.persistence.RegisteredMapper;
import com.outpost.payment.repository.PspEventResultRecord;
import java.util.List;
import org.apache.ibatis.annotations.Select;

/** Reads PSP event results from persistence. */
@RegisteredMapper
public interface PspEventResultStaticDataMapper {
  /** Returns all PSP event results in id order. */
  @Select("SELECT psp_event_result_id, code FROM psp_event_result ORDER BY psp_event_result_id")
  List<PspEventResultRecord> findAll();
}
