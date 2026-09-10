package com.outpost.accounting.repository.sanity;

import com.outpost.accounting.repository.TransactionEventTypeRecord;
import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;
import org.apache.ibatis.annotations.Select;

/** Reads transaction-event-type rows. */
@RegisteredMapper
public interface TransactionEventTypeStaticDataMapper {
  /** Reads all transaction-event-type rows. */
  @Select(
      """
      SELECT transaction_event_type_id, code, requires_journal_entry
      FROM transaction_event_type
      ORDER BY transaction_event_type_id
      """)
  List<TransactionEventTypeRecord> findAll();
}
