package com.outpost.account.repository;

import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;

/** Read-only MyBatis mapper for account types. */
@RegisteredMapper
public interface AccountTypeStaticDataReadMapper {
  /** Returns all records. */
  List<AccountTypeRecord> findAll();
}
