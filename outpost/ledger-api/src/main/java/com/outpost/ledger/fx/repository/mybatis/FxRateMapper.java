package com.outpost.ledger.fx.repository.mybatis;

import com.outpost.framework.persistence.RegisteredMapper;
import java.time.LocalDate;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

/** Queries persisted FX rates. */
@RegisteredMapper
public interface FxRateMapper {

  /**
   * Reads the row for one ordered currency pair and date.
   *
   * <p>The row type is package-private, so it is returned inside {@code Optional}: the mapper's JDK
   * proxy cannot access a package-private type declared directly as a return type.
   *
   * @return the row, or empty when none exists
   */
  Optional<FxRateRecord> findFxRateByPairAndRateDate(
      @Param("baseCurrencyId") long baseCurrencyId,
      @Param("quoteCurrencyId") long quoteCurrencyId,
      @Param("rateDate") LocalDate rateDate);
}
