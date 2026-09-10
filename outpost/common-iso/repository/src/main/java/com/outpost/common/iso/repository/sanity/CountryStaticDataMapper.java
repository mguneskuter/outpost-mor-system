package com.outpost.common.iso.repository.sanity;

import com.outpost.common.iso.repository.CountryRecord;
import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;
import org.apache.ibatis.annotations.Select;

/** Read-only MyBatis mapper for countries. */
@RegisteredMapper
public interface CountryStaticDataMapper {
  /** Returns all country records. */
  @Select("SELECT country_id, iso_code, name FROM country ORDER BY country_id")
  List<CountryRecord> findAll();
}
