package com.outpost.account.configuration.repository;

import com.outpost.persistence.RegisteredMapper;
import java.util.List;

/** Read-only MyBatis mapper for fee modes. */
@RegisteredMapper
public interface FeeModeStaticDataReadMapper {
  /** Returns all records. */
  List<FeeModeRecord> findAll();
}
