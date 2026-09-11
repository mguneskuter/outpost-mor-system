package com.outpost.fx.provider.cached;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.outpost.common.iso.Currencies;
import com.outpost.fx.FxRate;
import com.outpost.fx.repository.FxRateRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

class CachedFxRateProviderTest {

  private static final Currencies.Currency EUR = Currencies.EUR.getValue();
  private static final Currencies.Currency USD = Currencies.USD.getValue();
  private static final LocalDate DATE = LocalDate.of(2026, 9, 10);

  @Test
  void resolvesExactDateAndRejectsAbsentLookups() {
    var rate = new FxRate(1, EUR, USD, DATE, new BigDecimal("1.2300"), "ECB");
    var provider = new CachedFxRateProvider(new FakeFxRateRepository(rate));

    assertSame(rate, provider.getRate(EUR, USD, DATE));
    assertThrows(
        IllegalArgumentException.class, () -> provider.getRate(EUR, USD, DATE.minusDays(1)));
    assertThrows(IllegalArgumentException.class, () -> provider.getRate(USD, EUR, DATE));
  }

  @Test
  void resolutionIgnoresLaterRepositoryChanges() {
    var rates = new ArrayList<FxRate>();
    var rate = new FxRate(1, EUR, USD, DATE, new BigDecimal("1.2300"), "ECB");
    rates.add(rate);
    var provider = new CachedFxRateProvider(new FakeFxRateRepository(rates));
    rates.clear();

    assertSame(rate, provider.getRate(EUR, USD, DATE));
  }

  @Test
  void rejectsDuplicateKeysWithDifferentRates() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CachedFxRateProvider(
                new FakeFxRateRepository(
                    new FxRate(1, EUR, USD, DATE, new BigDecimal("1.2300"), "ECB"),
                    new FxRate(2, EUR, USD, DATE, new BigDecimal("1.2400"), "ECB"))));
  }

  @Test
  void keepsFirstRateWhenDuplicateHasIdenticalRate() {
    var rate = new FxRate(1, EUR, USD, DATE, new BigDecimal("1.2300"), "ECB");
    var duplicate = new FxRate(2, EUR, USD, DATE, new BigDecimal("1.23"), "OTHER");

    var provider = new CachedFxRateProvider(new FakeFxRateRepository(rate, duplicate));

    assertSame(rate, provider.getRate(EUR, USD, DATE));
  }

  @Test
  @SuppressWarnings("NullAway")
  void rejectsNullAndIdentityLookups() {
    var provider =
        new CachedFxRateProvider(
            new FakeFxRateRepository(new FxRate(1, EUR, USD, DATE, BigDecimal.ONE, "ECB")));
    assertThrows(NullPointerException.class, () -> provider.getRate(null, USD, DATE));
    assertThrows(NullPointerException.class, () -> provider.getRate(EUR, null, DATE));
    assertThrows(IllegalArgumentException.class, () -> provider.getRate(EUR, EUR, DATE));
    assertThrows(NullPointerException.class, () -> provider.getRate(EUR, USD, null));
  }

  @Test
  @SuppressWarnings("NullAway")
  void rejectsNullRepositoryAndNullRates() {
    assertThrows(NullPointerException.class, () -> new CachedFxRateProvider(null));
    var ratesWithNullElement = new ArrayList<FxRate>();
    ratesWithNullElement.add(null);
    assertThrows(
        NullPointerException.class,
        () -> new CachedFxRateProvider(new FakeFxRateRepository(ratesWithNullElement)));
  }

  private static final class FakeFxRateRepository implements FxRateRepository {

    private final List<FxRate> rates;

    private FakeFxRateRepository(List<FxRate> rates) {
      this.rates = rates;
    }

    private FakeFxRateRepository(FxRate... rates) {
      this(new ArrayList<>(List.of(rates)));
    }

    @Override
    public Collection<FxRate> findAll() {
      return rates;
    }
  }
}
