package com.outpost.ledger.fx.repository.mybatis;

import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.fx.FxFee;
import com.outpost.fx.repository.FxFeeRepository;
import java.util.List;
import java.util.Objects;

/** Converts persisted fee rows and rejects currency metadata that differs from the domain. */
public final class MyBatisFxFeeRepository implements FxFeeRepository {

  private final FxFeeMapper mapper;

  /**
   * Creates a repository backed by the supplied mapper.
   *
   * @param mapper source of persisted fee rows; must not be {@code null}
   */
  public MyBatisFxFeeRepository(FxFeeMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
  }

  /**
   * Reads every persisted fee and converts it to a domain value.
   *
   * @return the loaded fees, never {@code null}
   * @throws IllegalStateException when persisted currency metadata is unknown or divergent
   */
  @Override
  public List<FxFee> findAll() {
    return Objects.requireNonNull(mapper.findAll(), "FX fee rows").stream()
        .map(row -> toFxFee(Objects.requireNonNull(row, "FX fee row")))
        .toList();
  }

  private static FxFee toFxFee(FxFeeRecord row) {
    return new FxFee(
        row.fxFeeId(),
        currency(row.baseCurrencyId(), row.baseCurrencyCode(), row.baseCurrencyExponent()),
        currency(row.quoteCurrencyId(), row.quoteCurrencyCode(), row.quoteCurrencyExponent()),
        row.feeRateBps());
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
