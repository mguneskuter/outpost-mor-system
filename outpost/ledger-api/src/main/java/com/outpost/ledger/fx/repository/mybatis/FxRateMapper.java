package com.outpost.ledger.fx.repository.mybatis;

import com.outpost.framework.persistence.RegisteredMapper;
import java.util.List;

/** Queries persisted FX rates for startup loading. */
@RegisteredMapper
public interface FxRateMapper {

  /**
   * Reads rows ordered by date and currency pair.
   *
   * @return rows ordered by date and currency pair, never {@code null}
   */
  List<FxRateRecord> findAll();
}
