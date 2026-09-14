package com.outpost.fx.provider;

import com.outpost.common.iso.Currencies.Currency;
import java.time.LocalDate;

/** No exchange rate is stored for an ordered currency pair on a date. */
public final class MissingFxRateException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final Currency baseCurrency;
  private final Currency quoteCurrency;
  private final LocalDate rateDate;

  /**
   * Creates the failure for one exact lookup.
   *
   * @param baseCurrency the currency being exchanged
   * @param quoteCurrency the currency in which the rate is expressed
   * @param rateDate the date for which no rate is stored
   */
  public MissingFxRateException(Currency baseCurrency, Currency quoteCurrency, LocalDate rateDate) {
    super(
        "no FX rate for "
            + baseCurrency.getCurrencyCode()
            + "/"
            + quoteCurrency.getCurrencyCode()
            + " on "
            + rateDate);
    this.baseCurrency = baseCurrency;
    this.quoteCurrency = quoteCurrency;
    this.rateDate = rateDate;
  }

  /** Returns the currency being exchanged. */
  public Currency getBaseCurrency() {
    return baseCurrency;
  }

  /** Returns the currency in which the missing rate would be expressed. */
  public Currency getQuoteCurrency() {
    return quoteCurrency;
  }

  /** Returns the date for which no rate is stored. */
  public LocalDate getRateDate() {
    return rateDate;
  }
}
