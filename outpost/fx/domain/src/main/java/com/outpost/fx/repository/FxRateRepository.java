package com.outpost.fx.repository;

import com.outpost.common.iso.Currencies.Currency;
import com.outpost.fx.FxRate;
import java.time.LocalDate;
import java.util.Optional;

/** Reads the exchange rates available to the application. */
public interface FxRateRepository {

  /**
   * Returns the rate stored for the ordered pair on exactly {@code rateDate}.
   *
   * @return the stored rate, or empty when none is stored for that pair and date
   */
  Optional<FxRate> findFxRateByPairAndRateDate(
      Currency baseCurrency, Currency quoteCurrency, LocalDate rateDate);
}
