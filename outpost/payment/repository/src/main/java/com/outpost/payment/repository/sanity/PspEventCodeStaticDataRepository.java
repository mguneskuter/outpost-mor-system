package com.outpost.payment.repository.sanity;

import com.outpost.payment.PspEventCodes;
import com.outpost.payment.PspEventCodes.PspEventCode;
import com.outpost.payment.repository.PspEventCodeRecord;
import com.outpost.platform.staticdata.StaticDataRepository;
import java.util.List;

final class PspEventCodeStaticDataRepository
    implements StaticDataRepository<PspEventCodes, PspEventCode, PspEventCodeRecord> {
  private final PspEventCodeStaticDataMapper mapper;

  PspEventCodeStaticDataRepository(PspEventCodeStaticDataMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Class<PspEventCodes> staticDataEnum() {
    return PspEventCodes.class;
  }

  @Override
  public String table() {
    return "psp_event_code";
  }

  @Override
  public PspEventCode enumValue(PspEventCodes constant) {
    return constant.getValue();
  }

  @Override
  public PspEventCodeRecord toDatabaseRecord(PspEventCode value) {
    return new PspEventCodeRecord(value.pspEventCodeId(), value.code());
  }

  @Override
  public PspEventCode toDomainValue(PspEventCodeRecord record) {
    return java.util.Arrays.stream(PspEventCodes.values())
        .map(PspEventCodes::getValue)
        .filter(
            value ->
                value.pspEventCodeId() == record.pspEventCodeId()
                    && value.code().equals(record.code()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "PSP event code record does not match the enum: " + record));
  }

  @Override
  public long id(PspEventCodeRecord record) {
    return record.pspEventCodeId();
  }

  @Override
  public List<PspEventCodeRecord> findAll() {
    return mapper.findAll();
  }
}
