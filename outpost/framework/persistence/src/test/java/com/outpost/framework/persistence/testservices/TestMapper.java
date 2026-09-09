package com.outpost.framework.persistence.testservices;

import com.outpost.framework.persistence.RegisteredMapper;
import org.apache.ibatis.annotations.Select;

/**
 * A minimal MyBatis mapper used to prove that {@link RegisteredMapper}-annotated interfaces are
 * discovered and execute queries.
 */
@RegisteredMapper
public interface TestMapper {

  /** Returns the integer one from a trivial {@code SELECT 1} query. */
  @Select("SELECT 1")
  int one();
}
