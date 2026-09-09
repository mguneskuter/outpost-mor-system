package com.outpost.platform.staticdata.job.repository;

import com.outpost.framework.persistence.RegisteredMapper;
import com.outpost.payment.common.repository.ProductTypeRecord;

/** Job-only insert mapper for product types. */
@RegisteredMapper
public interface ProductTypeStaticDataInsertMapper {
  /** Inserts one record. */
  int insert(ProductTypeRecord record);
}
