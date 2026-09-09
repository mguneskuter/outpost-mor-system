package com.outpost.common.iso.repository;

import com.outpost.persistence.RegisteredMapper;
import java.util.List;

/** Read-only MyBatis mapper for country subdivisions. */
@RegisteredMapper
public interface CountrySubdivisionStaticDataReadMapper {
  /** Returns all records. */
  List<CountrySubdivisionRecord> findAll();
}
