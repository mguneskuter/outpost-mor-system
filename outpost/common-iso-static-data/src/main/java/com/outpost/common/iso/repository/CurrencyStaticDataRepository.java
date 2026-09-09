package com.outpost.common.iso.repository;

import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.platform.staticdata.StaticDataRepository;
import java.util.List;

/** Typed repository for currencies. */
final class CurrencyStaticDataRepository
    implements StaticDataRepository<Currencies, Currency, CurrencyRecord> {
  private final CurrencyStaticDataReadMapper mapper;

  /** Creates a typed repository. */
  CurrencyStaticDataRepository(CurrencyStaticDataReadMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Class<Currencies> staticDataEnum() {
    return Currencies.class;
  }

  @Override
  public String table() {
    return "currency";
  }

  @Override
  public Currency enumValue(Currencies constant) {
    return constant.value();
  }

  @Override
  public CurrencyRecord toDatabaseRecord(Currency value) {
    return new CurrencyRecord(value.currencyId(), value.currencyCode(), value.exponent());
  }

  @Override
  public Currency toDomainValue(CurrencyRecord record) {
    return Currencies.fromCurrencyCode(record.currencyCode())
        .filter(
            value ->
                value.currencyId() == record.currencyId() && value.exponent() == record.exponent())
        .orElseThrow(
            () ->
                new IllegalArgumentException("Currency record does not match the enum: " + record));
  }

  @Override
  public long id(CurrencyRecord record) {
    return record.currencyId();
  }

  @Override
  public List<CurrencyRecord> findAll() {
    return mapper.findAll();
  }
}
