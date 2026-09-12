package com.outpost.accounting.queue.repository.sanity;

import com.outpost.accounting.queue.repository.AccountingRequestStatusRecord;
import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;
import org.apache.ibatis.annotations.Select;

/** Reads accounting request status rows. */
@RegisteredMapper
public interface AccountingRequestStatusStaticDataMapper {
  /** Reads all accounting request status rows. */
  @Select(
      "SELECT accounting_request_status_id, accounting_request_status_code "
          + "FROM accounting_request_status ORDER BY accounting_request_status_id")
  List<AccountingRequestStatusRecord> findAll();
}
