package com.outpost.fx.repository.mybatis;

import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;

/** Queries persisted FX fees for startup loading. */
@RegisteredMapper
public interface FxFeeMapper {

  /**
   * Reads rows ordered by currency pair.
   *
   * @return rows ordered by currency pair, never {@code null}
   */
  List<FxFee> findFxFees();
}
