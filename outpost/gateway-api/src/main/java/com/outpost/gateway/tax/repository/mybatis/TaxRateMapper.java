package com.outpost.gateway.tax.repository.mybatis;

import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;

/** Maps tax-rate database rows. */
@RegisteredMapper
public interface TaxRateMapper {

  /** Returns tax-rate database rows. */
  List<TaxRateRow> findAll();
}
