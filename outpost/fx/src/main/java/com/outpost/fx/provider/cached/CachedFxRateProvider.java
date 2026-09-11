package com.outpost.fx.provider.cached;

import com.outpost.common.iso.Currencies.Currency;
import com.outpost.fx.FxRate;
import com.outpost.fx.provider.FxRateProvider;
import com.outpost.fx.repository.FxRateRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** In-memory exchange-rate provider backed by rates read from a repository. */
public final class CachedFxRateProvider implements FxRateProvider {

  private static final Logger LOG = LoggerFactory.getLogger(CachedFxRateProvider.class);

  private final Map<RateKey, FxRate> rates;

  /**
   * Builds an immutable exact-date index from the rates the repository returns.
   *
   * <p>When two rates share a key, the first one is kept: if the later rate is numerically
   * identical, a warning is logged; otherwise construction fails.
   *
   * @param repository the repository that returns the rates to index
   * @throws NullPointerException if {@code repository} is null or returns a null rate
   * @throws IllegalArgumentException if two rates share a key but differ in rate
   */
  public CachedFxRateProvider(FxRateRepository repository) {
    Objects.requireNonNull(repository, "repository");
    Map<RateKey, FxRate> indexed = new HashMap<>();
    for (FxRate fxRate : repository.findAll()) {
      Objects.requireNonNull(fxRate, "fxRate");
      RateKey key = new RateKey(fxRate.baseCurrency(), fxRate.quoteCurrency(), fxRate.rateDate());
      FxRate existing = indexed.putIfAbsent(key, fxRate);
      if (existing != null) {
        if (existing.rate().compareTo(fxRate.rate()) == 0) {
          LOG.warn(
              "duplicate FX rate key with identical rate; keeping the existing entry: {}", key);
        } else {
          throw new IllegalArgumentException("duplicate FX rate key: " + key);
        }
      }
    }
    rates = Map.copyOf(indexed);
  }

  @Override
  public FxRate getRate(Currency baseCurrency, Currency quoteCurrency, LocalDate asOf) {
    Objects.requireNonNull(baseCurrency, "baseCurrency");
    Objects.requireNonNull(quoteCurrency, "quoteCurrency");
    Objects.requireNonNull(asOf, "asOf");
    if (baseCurrency.equals(quoteCurrency)) {
      throw new IllegalArgumentException("baseCurrency must differ from quoteCurrency");
    }
    FxRate rate = rates.get(new RateKey(baseCurrency, quoteCurrency, asOf));
    if (rate == null) {
      throw new IllegalArgumentException("no FX rate for currency pair and date");
    }
    return rate;
  }

  private record RateKey(Currency baseCurrency, Currency quoteCurrency, LocalDate rateDate) {}
}
