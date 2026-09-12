package com.outpost.payment.repository.sanity;

import com.outpost.payment.PspEventResults;
import com.outpost.payment.PspEventResults.PspEventResult;
import com.outpost.payment.repository.PspEventResultRecord;
import com.outpost.platform.staticdata.StaticDataRepository;
import java.util.List;

final class PspEventResultStaticDataRepository
    implements StaticDataRepository<PspEventResults, PspEventResult, PspEventResultRecord> {
  private final PspEventResultStaticDataMapper mapper;

  PspEventResultStaticDataRepository(PspEventResultStaticDataMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Class<PspEventResults> staticDataEnum() {
    return PspEventResults.class;
  }

  @Override
  public String table() {
    return "psp_event_result";
  }

  @Override
  public PspEventResult enumValue(PspEventResults constant) {
    return constant.getValue();
  }

  @Override
  public PspEventResultRecord toDatabaseRecord(PspEventResult value) {
    return new PspEventResultRecord(value.pspEventResultId(), value.code());
  }

  @Override
  public PspEventResult toDomainValue(PspEventResultRecord record) {
    return java.util.Arrays.stream(PspEventResults.values())
        .map(PspEventResults::getValue)
        .filter(
            value ->
                value.pspEventResultId() == record.pspEventResultId()
                    && value.code().equals(record.code()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "PSP event result record does not match the enum: " + record));
  }

  @Override
  public long id(PspEventResultRecord record) {
    return record.pspEventResultId();
  }

  @Override
  public List<PspEventResultRecord> findAll() {
    return mapper.findAll();
  }
}
