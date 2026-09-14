package com.outpost.accounting.repository.sanity;

import com.outpost.accounting.repository.AccountTypeRegisterTypeRecord;
import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;
import org.apache.ibatis.annotations.Select;

/** Reads account-type and register-type rows. */
@RegisteredMapper
public interface AccountTypeRegisterTypeStaticDataMapper {
  /** Reads all account-type and register-type rows. */
  @Select(
      "SELECT account_type_register_type_id, account_type_id, register_type_id "
          + "FROM account_type_register_type ORDER BY account_type_register_type_id")
  List<AccountTypeRegisterTypeRecord> findAll();
}
