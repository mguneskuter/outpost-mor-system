package com.outpost.accounting.repository.sanity;

import com.outpost.accounting.repository.TransactionTypeRecord;
import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;
import org.apache.ibatis.annotations.Select;

/** Reads transaction-type rows. */
@RegisteredMapper
public interface TransactionTypeStaticDataMapper {
  /** Reads all transaction-type rows. */
  @Select("SELECT transaction_type_id, code FROM transaction_type ORDER BY transaction_type_id")
  List<TransactionTypeRecord> findAll();
}
