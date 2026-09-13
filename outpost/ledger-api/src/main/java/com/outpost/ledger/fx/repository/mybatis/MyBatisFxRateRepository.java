package com.outpost.ledger.fx.repository.mybatis;

import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.fx.FxRate;
import com.outpost.fx.repository.FxRateRepository;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Converts persisted rate rows and rejects currency metadata that differs from the domain. */
public final class MyBatisFxRateRepository implements FxRateRepository {

  private final FxRateMapper mapper;

  /**
   * Creates a repository backed by the supplied mapper.
   *
   * @param mapper source of persisted rate rows; must not be {@code null}
   */
  public MyBatisFxRateRepository(FxRateMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalStateException when persisted currency metadata is unknown or divergent
   */
  @Override
  public Optional<FxRate> findFxRateByPairAndRateDate(
      Currency baseCurrency, Currency quoteCurrency, LocalDate rateDate) {
    return mapper
        .findFxRateByPairAndRateDate(
            baseCurrency.getCurrencyId(), quoteCurrency.getCurrencyId(), rateDate)
        .map(MyBatisFxRateRepository::toFxRate);
  }

  private static FxRate toFxRate(FxRateRecord row) {
    return new FxRate(
        row.fxRateId(),
        currency(row.baseCurrencyId(), row.baseCurrencyCode(), row.baseCurrencyExponent()),
        currency(row.quoteCurrencyId(), row.quoteCurrencyCode(), row.quoteCurrencyExponent()),
        row.rateDate(),
        row.rate(),
        row.source());
  }

  private static Currency currency(long id, String code, int exponent) {
    return Currencies.fromCurrencyCode(code)
        .filter(
            value ->
                value.getCurrencyId() == id
                    && value.getCurrencyCode().equals(code)
                    && value.getExponent() == exponent)
        .orElseThrow(() -> new IllegalStateException("unknown or divergent currency: " + code));
  }
}
