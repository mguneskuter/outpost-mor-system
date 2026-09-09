package com.outpost.payment.common.repository;

import com.outpost.persistence.RegisteredMapper;
import java.util.List;

/** Read-only MyBatis mapper for product types. */
@RegisteredMapper
public interface ProductTypeStaticDataReadMapper {
  /** Returns all records. */
  List<ProductTypeRecord> findAll();
}
