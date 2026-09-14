package com.outpost.accounting.repository.sanity;

import com.outpost.accounting.repository.RegisterTypeRecord;
import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;
import org.apache.ibatis.annotations.Select;

/** Reads register-type rows. */
@RegisteredMapper
public interface RegisterTypeStaticDataMapper {
  /** Reads all register-type rows. */
  @Select(
      "SELECT register_type_id, register_type_code FROM register_type ORDER BY register_type_id")
  List<RegisterTypeRecord> findAll();
}
