package com.outpost.common.iso.repository;

import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;

/** Read-only MyBatis mapper for countries. */
@RegisteredMapper
public interface CountryStaticDataReadMapper {
  /** Returns all records. */
  List<CountryRecord> findAll();
}
