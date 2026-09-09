package com.outpost.platform.staticdata.job.repository;

import com.outpost.payment.common.repository.ProductTypeRecord;
import com.outpost.persistence.RegisteredMapper;

/** Job-only insert mapper for product types. */
@RegisteredMapper
public interface ProductTypeStaticDataInsertMapper {
  /** Inserts one record. */
  int insert(ProductTypeRecord record);
}
