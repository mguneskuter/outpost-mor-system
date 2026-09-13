package com.outpost.accounting.queue.repository.sanity;

import com.outpost.accounting.queue.repository.AccountingRequestResultRecord;
import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;
import org.apache.ibatis.annotations.Select;

/** Reads accounting request result rows. */
@RegisteredMapper
public interface AccountingRequestResultStaticDataMapper {
  /** Reads all accounting request result rows. */
  @Select(
      "SELECT accounting_request_result_type_id, accounting_request_result_type_code "
          + "FROM accounting_request_result_type ORDER BY accounting_request_result_type_id")
  List<AccountingRequestResultRecord> findAll();
}
