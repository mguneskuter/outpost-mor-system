package com.outpost.payment.common.repository;

import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;

/** Read-only MyBatis mapper for product types. */
@RegisteredMapper
public interface ProductTypeStaticDataReadMapper {
  /** Returns all records. */
  List<ProductTypeRecord> findAll();
}
