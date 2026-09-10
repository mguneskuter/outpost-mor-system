package com.outpost.payment.common.repository.sanity;

import com.outpost.framework.persistence.RegisteredMapper;
import com.outpost.payment.common.repository.ProductTypeRecord;
import java.util.List;
import org.apache.ibatis.annotations.Select;

/** Read-only MyBatis mapper for product types. */
@RegisteredMapper
public interface ProductTypeStaticDataMapper {
  /** Returns all product-type records. */
  @Select("SELECT product_type_id, code FROM product_type ORDER BY product_type_id")
  List<ProductTypeRecord> findAll();
}
