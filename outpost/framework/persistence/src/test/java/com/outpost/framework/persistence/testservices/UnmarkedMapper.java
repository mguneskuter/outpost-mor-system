package com.outpost.framework.persistence.testservices;

/**
 * A mapper-shaped interface that intentionally lacks {@link
 * com.outpost.framework.persistence.RegisteredMapper}. It must not be discovered or registered as a
 * MyBatis mapper.
 */
public interface UnmarkedMapper {

  /** Returns the integer one, mirroring {@link TestMapper} but without the shared marker. */
  int one();
}
