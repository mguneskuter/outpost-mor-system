package com.outpost.accounting.queue.repository.sanity;

import com.outpost.accounting.queue.repository.AccountingRequestTypeRecord;
import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;
import org.apache.ibatis.annotations.Select;

/** Reads accounting request type rows. */
@RegisteredMapper
public interface AccountingRequestTypeStaticDataMapper {
  /** Reads all accounting request type rows. */
  @Select(
      "SELECT accounting_request_type_id, accounting_request_type_code "
          + "FROM accounting_request_type ORDER BY accounting_request_type_id")
  List<AccountingRequestTypeRecord> findAll();
}
