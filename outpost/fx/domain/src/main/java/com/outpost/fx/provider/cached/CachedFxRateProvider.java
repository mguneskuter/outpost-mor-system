package com.outpost.fx.provider.cached;

import com.outpost.common.iso.Currencies.Currency;
import com.outpost.fx.FxRate;
import com.outpost.fx.provider.FxRateProvider;
import com.outpost.fx.provider.MissingFxRateException;
import com.outpost.fx.repository.FxRateRepository;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Reads exchange rates through a repository and keeps resolved rates in memory, up to a bound.
 *
 * <p>A stored rate is immutable evidence, so a resolved rate is served from memory until it is
 * evicted. A lookup that finds no rate is not remembered: a rate stored later is resolved by the
 * next lookup.
 *
 * <p>Safe for concurrent use. The repository is never called inside a map operation. Concurrent
 * lookups that miss the same rate may each read the repository; a lookup that then finds a rate
 * already held returns that rate instead of its own read. Once the bound is exceeded, arbitrary
 * entries are evicted until the number held is back within the bound; while lookups race, it can
 * briefly exceed the bound.
 */
public final class CachedFxRateProvider implements FxRateProvider {

  private final FxRateRepository repository;
  private final int bound;
  private final ConcurrentMap<RateKey, FxRate> rates = new ConcurrentHashMap<>();

  /**
   * Creates a provider that holds at most {@code bound} resolved rates, apart from a transient
   * excess while concurrent lookups race.
   *
   * @throws IllegalArgumentException if {@code bound} is not positive
   */
  public CachedFxRateProvider(FxRateRepository repository, int bound) {
    this.repository = Objects.requireNonNull(repository, "repository");
    if (bound <= 0) {
      throw new IllegalArgumentException("bound must be positive: " + bound);
    }
    this.bound = bound;
  }

  @Override
  public FxRate getRate(Currency baseCurrency, Currency quoteCurrency, LocalDate asOf) {
    Objects.requireNonNull(baseCurrency, "baseCurrency");
    Objects.requireNonNull(quoteCurrency, "quoteCurrency");
    Objects.requireNonNull(asOf, "asOf");
    if (baseCurrency.equals(quoteCurrency)) {
      throw new IllegalArgumentException("baseCurrency must differ from quoteCurrency");
    }
    RateKey key = new RateKey(baseCurrency, quoteCurrency, asOf);
    FxRate cached = rates.get(key);
    if (cached != null) {
      return cached;
    }
    // Not computeIfAbsent: it would hold a map bin lock for the whole repository read.
    FxRate read =
        repository
            .findFxRateByPairAndRateDate(baseCurrency, quoteCurrency, asOf)
            .orElseThrow(() -> new MissingFxRateException(baseCurrency, quoteCurrency, asOf));
    FxRate stored = rates.putIfAbsent(key, read);
    if (stored != null) {
      return stored;
    }
    evictBeyondBound();
    return read;
  }

  private void evictBeyondBound() {
    Iterator<RateKey> keys = rates.keySet().iterator();
    while (rates.size() > bound && keys.hasNext()) {
      keys.next();
      keys.remove();
    }
  }

  private record RateKey(Currency baseCurrency, Currency quoteCurrency, LocalDate rateDate) {}
}
