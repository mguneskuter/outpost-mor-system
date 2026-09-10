package com.outpost.account.configuration.repository.sanity;

import com.outpost.account.configuration.repository.FeeModeRecord;
import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;
import org.apache.ibatis.annotations.Select;

/** Read-only MyBatis mapper for fee modes. */
@RegisteredMapper
public interface FeeModeStaticDataMapper {
  /** Returns all fee-mode records. */
  @Select("SELECT fee_mode_id, code FROM fee_mode ORDER BY fee_mode_id")
  List<FeeModeRecord> findAll();
}
