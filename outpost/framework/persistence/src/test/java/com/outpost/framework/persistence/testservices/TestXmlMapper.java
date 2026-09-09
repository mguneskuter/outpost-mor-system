package com.outpost.framework.persistence.testservices;

import com.outpost.framework.persistence.RegisteredMapper;

/**
 * A mapper whose SQL is handcrafted in a {@code db/mapper} XML file, proving the shared XML-mapper
 * registration mechanism loads XML-backed mappers without per-module wiring.
 */
@RegisteredMapper
public interface TestXmlMapper {

  /** Returns the integer one via the {@code db/mapper/TestXmlMapper.xml} mapped statement. */
  int oneViaXml();
}
