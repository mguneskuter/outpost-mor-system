package com.outpost.common.iso.repository.sanity;

import com.outpost.common.iso.repository.CountrySubdivisionRecord;
import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;
import org.apache.ibatis.annotations.Select;

/** Read-only MyBatis mapper for country subdivisions. */
@RegisteredMapper
public interface CountrySubdivisionStaticDataMapper {
  /** Returns all country-subdivision records. */
  @Select(
      """
      SELECT country_subdivision_id, country_id, code, name
      FROM country_subdivision
      ORDER BY country_subdivision_id
      """)
  List<CountrySubdivisionRecord> findAll();
}
