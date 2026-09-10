package com.outpost.account.configuration.repository.sanity;

import com.outpost.account.configuration.FeeModes;
import com.outpost.account.configuration.FeeModes.FeeMode;
import com.outpost.account.configuration.repository.FeeModeRecord;
import com.outpost.platform.staticdata.StaticDataRepository;
import java.util.List;

/** Typed repository for fee modes. */
final class FeeModeStaticDataRepository
    implements StaticDataRepository<FeeModes, FeeMode, FeeModeRecord> {
  private final FeeModeStaticDataMapper mapper;

  /** Creates a typed repository. */
  FeeModeStaticDataRepository(FeeModeStaticDataMapper mapper) {
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
    return constant.getValue();
  }

  @Override
  public FeeModeRecord toDatabaseRecord(FeeMode value) {
    return new FeeModeRecord(value.getFeeModeId(), value.getCode());
  }

  @Override
  public FeeMode toDomainValue(FeeModeRecord record) {
    return FeeModes.fromCode(record.code())
        .filter(value -> value.getFeeModeId() == record.feeModeId())
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
