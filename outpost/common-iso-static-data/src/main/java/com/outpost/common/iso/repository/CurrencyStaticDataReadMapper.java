package com.outpost.common.iso.repository;

import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;

/** Read-only MyBatis mapper for currencies. */
@RegisteredMapper
public interface CurrencyStaticDataReadMapper {
  /** Returns all records. */
  List<CurrencyRecord> findAll();
}
