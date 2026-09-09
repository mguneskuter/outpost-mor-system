package com.outpost.account.configuration.repository;

import com.outpost.account.configuration.FeeModes;
import com.outpost.account.configuration.FeeModes.FeeMode;
import com.outpost.platform.staticdata.StaticDataRepository;
import java.util.List;

/** Typed repository for fee modes. */
final class FeeModeStaticDataRepository
    implements StaticDataRepository<FeeModes, FeeMode, FeeModeRecord> {
  private final FeeModeStaticDataReadMapper mapper;

  /** Creates a typed repository. */
  FeeModeStaticDataRepository(FeeModeStaticDataReadMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Class<FeeModes> staticDataEnum() {
    return FeeModes.class;
  }

  @Override
  public String table() {
    return "fee_mode";
  }

  @Override
  public FeeMode enumValue(FeeModes constant) {
    return constant.value();
  }

  @Override
  public FeeModeRecord toDatabaseRecord(FeeMode value) {
    return new FeeModeRecord(value.feeModeId(), value.code());
  }

  @Override
  public FeeMode toDomainValue(FeeModeRecord record) {
    return FeeModes.fromCode(record.code())
        .filter(value -> value.feeModeId() == record.feeModeId())
        .orElseThrow(
            () ->
                new IllegalArgumentException("Fee mode record does not match the enum: " + record));
  }

  @Override
  public long id(FeeModeRecord record) {
    return record.feeModeId();
  }

  @Override
  public List<FeeModeRecord> findAll() {
    return mapper.findAll();
  }
}
