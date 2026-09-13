package com.outpost.fx.provider.cached;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.outpost.common.iso.Currencies;
import com.outpost.fx.FxRate;
import com.outpost.fx.provider.MissingFxRateException;
import com.outpost.fx.repository.FxRateRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CachedFxRateProviderTest {

  private static final Currencies.Currency EUR = Currencies.EUR.getValue();
  private static final Currencies.Currency USD = Currencies.USD.getValue();
  private static final Currencies.Currency GBP = Currencies.GBP.getValue();
  private static final LocalDate DATE = LocalDate.of(2026, 9, 10);

  @Test
  void resolvesExactDateAndOrderedPairOnly() {
    var rate = new FxRate(1, EUR, USD, DATE, new BigDecimal("1.2300"), "ECB");
    var provider = new CachedFxRateProvider(new StoredRates(rate), 10);

    assertSame(rate, provider.getRate(EUR, USD, DATE));
    assertThrows(MissingFxRateException.class, () -> provider.getRate(EUR, USD, DATE.minusDays(1)));
    assertThrows(MissingFxRateException.class, () -> provider.getRate(USD, EUR, DATE));
  }

  @Test
  void namesThePairAndDateOfMissingRate() {
    var provider = new CachedFxRateProvider(new StoredRates(), 10);

    var missing =
        assertThrows(MissingFxRateException.class, () -> provider.getRate(USD, EUR, DATE));

    assertSame(USD, missing.getBaseCurrency());
    assertSame(EUR, missing.getQuoteCurrency());
    assertEquals(DATE, missing.getRateDate());
  }

  @Test
  void resolvesRateStoredAfterLookupFoundNone() {
    var storedRates = new StoredRates();
    var provider = new CachedFxRateProvider(storedRates, 10);
    assertThrows(MissingFxRateException.class, () -> provider.getRate(EUR, USD, DATE));

    var rate = new FxRate(1, EUR, USD, DATE, new BigDecimal("1.2300"), "ECB");
    storedRates.store(rate);

    assertSame(rate, provider.getRate(EUR, USD, DATE));
  }

  @Test
  void holdsNoMoreResolvedRatesThanItsBound() {
    var eurUsd = new FxRate(1, EUR, USD, DATE, new BigDecimal("1.2300"), "ECB");
    var eurGbp = new FxRate(2, EUR, GBP, DATE, new BigDecimal("0.8600"), "ECB");
    var storedRates = new StoredRates(eurUsd, eurGbp);
    var provider = new CachedFxRateProvider(storedRates, 1);
    provider.getRate(EUR, USD, DATE);
    provider.getRate(EUR, GBP, DATE);

    storedRates.clear();

    int evicted = 0;
    for (Currencies.Currency quote : List.of(USD, GBP)) {
      try {
        provider.getRate(EUR, quote, DATE);
      } catch (MissingFxRateException missing) {
        evicted++;
      }
    }
    assertTrue(evicted >= 1, "both resolved rates are still held with a bound of one");
  }

  @Test
  void concurrentMissesForSameRateReturnOneStoredValue() throws Exception {
    var arrivals = new CountDownLatch(2);
    var readCount = new AtomicInteger();
    FxRateRepository repository =
        (base, quote, rateDate) -> {
          long fxRateId = readCount.incrementAndGet();
          arrivals.countDown();
          try {
            if (!arrivals.await(5, TimeUnit.SECONDS)) {
              throw new IllegalStateException("second lookup did not reach the repository");
            }
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
          }
          return Optional.of(
              new FxRate(fxRateId, base, quote, rateDate, new BigDecimal("1.2300"), "ECB"));
        };
    var provider = new CachedFxRateProvider(repository, 10);

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<FxRate> first = executor.submit(() -> provider.getRate(EUR, USD, DATE));
      Future<FxRate> second = executor.submit(() -> provider.getRate(EUR, USD, DATE));

      FxRate firstRate = first.get(5, TimeUnit.SECONDS);
      assertSame(firstRate, second.get(5, TimeUnit.SECONDS));
      assertSame(firstRate, provider.getRate(EUR, USD, DATE));
    }
  }

  @Test
  @SuppressWarnings("NullAway")
  void rejectsNullAndIdentityLookups() {
    var provider = new CachedFxRateProvider(new StoredRates(), 10);
    assertThrows(NullPointerException.class, () -> provider.getRate(null, USD, DATE));
    assertThrows(NullPointerException.class, () -> provider.getRate(EUR, null, DATE));
    assertThrows(IllegalArgumentException.class, () -> provider.getRate(EUR, EUR, DATE));
    assertThrows(NullPointerException.class, () -> provider.getRate(EUR, USD, null));
  }

  @Test
  @SuppressWarnings("NullAway")
  void rejectsNullRepositoryAndNonPositiveBound() {
    assertThrows(NullPointerException.class, () -> new CachedFxRateProvider(null, 10));
    assertThrows(
        IllegalArgumentException.class, () -> new CachedFxRateProvider(new StoredRates(), 0));
  }

  private static final class StoredRates implements FxRateRepository {

    private final Map<List<Object>, FxRate> rates = new HashMap<>();

    private StoredRates(FxRate... rates) {
      for (FxRate rate : rates) {
        store(rate);
      }
    }

    private void store(FxRate rate) {
      rates.put(List.of(rate.baseCurrency(), rate.quoteCurrency(), rate.rateDate()), rate);
    }

    private void clear() {
      rates.clear();
    }

    @Override
    public Optional<FxRate> findFxRateByPairAndRateDate(
        Currencies.Currency baseCurrency, Currencies.Currency quoteCurrency, LocalDate rateDate) {
      return Optional.ofNullable(rates.get(List.of(baseCurrency, quoteCurrency, rateDate)));
    }
  }
}
