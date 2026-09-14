package com.outpost.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.outpost.common.iso.Currencies;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FxDomainTest {

  private static final Currencies.Currency EUR = Currencies.EUR.getValue();
  private static final Currencies.Currency USD = Currencies.USD.getValue();
  private static final LocalDate DATE = LocalDate.of(2026, 9, 10);

  @Test
  void retainsRateValuesExactly() {
    var rate =
        new FxRate(1, EUR, USD, LocalDate.of(2026, 9, 10), new BigDecimal("1.2300"), " ECB ");
    assertEquals(new BigDecimal("1.2300"), rate.rate());
    assertEquals(" ECB ", rate.source());
  }

  @Test
  void rejectsInvalidRateValues() {
    assertThrows(
        IllegalArgumentException.class, () -> new FxRate(0, EUR, USD, DATE, BigDecimal.ONE, "ECB"));
    assertThrows(
        IllegalArgumentException.class, () -> new FxRate(1, EUR, EUR, DATE, BigDecimal.ONE, "ECB"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new FxRate(1, EUR, USD, DATE, BigDecimal.ZERO, "ECB"));
    assertThrows(
        IllegalArgumentException.class, () -> new FxRate(1, EUR, USD, DATE, BigDecimal.ONE, "  "));
  }

  @Test
  @SuppressWarnings("NullAway")
  void rejectsNullRateValues() {
    assertThrows(
        NullPointerException.class, () -> new FxRate(1, null, USD, DATE, BigDecimal.ONE, "ECB"));
    assertThrows(
        NullPointerException.class, () -> new FxRate(1, EUR, null, DATE, BigDecimal.ONE, "ECB"));
    assertThrows(
        NullPointerException.class, () -> new FxRate(1, EUR, USD, null, BigDecimal.ONE, "ECB"));
    assertThrows(NullPointerException.class, () -> new FxRate(1, EUR, USD, DATE, null, "ECB"));
    assertThrows(
        NullPointerException.class, () -> new FxRate(1, EUR, USD, DATE, BigDecimal.ONE, null));
  }

  @Test
  void retainsZeroFeeRate() {
    var fee = new FxFee(1, EUR, USD, 0);
    assertEquals(0, fee.feeRateBps());
  }

  @Test
  void rejectsInvalidFeeValues() {
    assertThrows(IllegalArgumentException.class, () -> new FxFee(0, EUR, USD, 0));
    assertThrows(IllegalArgumentException.class, () -> new FxFee(1, EUR, EUR, 0));
    assertThrows(IllegalArgumentException.class, () -> new FxFee(1, EUR, USD, -1));
  }

  @Test
  @SuppressWarnings("NullAway")
  void rejectsNullFeeCurrencies() {
    assertThrows(NullPointerException.class, () -> new FxFee(1, null, USD, 0));
    assertThrows(NullPointerException.class, () -> new FxFee(1, EUR, null, 0));
  }
}
