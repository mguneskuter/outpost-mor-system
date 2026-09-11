package com.outpost.fx.provider;

import com.outpost.common.iso.Currencies.Currency;
import com.outpost.fx.FxRate;
import java.time.LocalDate;

/** Returns the exchange rate for an ordered currency pair and date without I/O. */
public interface FxRateProvider {

  /**
   * Returns the rate whose date exactly matches {@code asOf}; no earlier-date fallback is used.
   * Identity pairs are not resolved.
   *
   * @param baseCurrency the currency being exchanged
   * @param quoteCurrency the currency in which the rate is expressed
   * @param asOf the exact rate date
   * @return the matching rate
   * @throws IllegalArgumentException if the pair is identical or no exact rate exists
   */
  FxRate getRate(Currency baseCurrency, Currency quoteCurrency, LocalDate asOf);
}
