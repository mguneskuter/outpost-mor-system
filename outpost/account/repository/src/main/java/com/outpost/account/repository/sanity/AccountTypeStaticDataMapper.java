package com.outpost.account.repository.sanity;

import com.outpost.account.repository.AccountTypeRecord;
import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;
import org.apache.ibatis.annotations.Select;

/** Read-only MyBatis mapper for account types. */
@RegisteredMapper
public interface AccountTypeStaticDataMapper {
  /** Returns all account-type records. */
  @Select("SELECT account_type_id, code FROM account_type ORDER BY account_type_id")
  List<AccountTypeRecord> findAll();
}
