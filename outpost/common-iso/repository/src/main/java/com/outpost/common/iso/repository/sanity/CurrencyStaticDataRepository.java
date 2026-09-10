package com.outpost.common.iso.repository.sanity;

import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.common.iso.repository.CurrencyRecord;
import com.outpost.platform.staticdata.StaticDataRepository;
import java.util.List;

/** Typed repository for currencies. */
final class CurrencyStaticDataRepository
    implements StaticDataRepository<Currencies, Currency, CurrencyRecord> {
  private final CurrencyStaticDataMapper mapper;

  /** Creates a typed repository. */
  CurrencyStaticDataRepository(CurrencyStaticDataMapper mapper) {
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
    return constant.getValue();
  }

  @Override
  public CurrencyRecord toDatabaseRecord(Currency value) {
    return new CurrencyRecord(value.getCurrencyId(), value.getCurrencyCode(), value.getExponent());
  }

  @Override
  public Currency toDomainValue(CurrencyRecord record) {
    return Currencies.fromCurrencyCode(record.currencyCode())
        .filter(
            value ->
                value.getCurrencyId() == record.currencyId()
                    && value.getExponent() == record.exponent())
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
