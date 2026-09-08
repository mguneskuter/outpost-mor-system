package com.outpost.persistence.testservices;

import com.outpost.persistence.CommonMapper;
import org.apache.ibatis.annotations.Select;

/**
 * A minimal MyBatis mapper used to prove that {@link CommonMapper}-annotated interfaces are
 * discovered and execute queries.
 */
@CommonMapper
public interface TestMapper {

  /** Returns the integer one from a trivial {@code SELECT 1} query. */
  @Select("SELECT 1")
  int one();
}
