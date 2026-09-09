package com.outpost.account.configuration.repository;

import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;

/** Read-only MyBatis mapper for fee modes. */
@RegisteredMapper
public interface FeeModeStaticDataReadMapper {
  /** Returns all records. */
  List<FeeModeRecord> findAll();
}
