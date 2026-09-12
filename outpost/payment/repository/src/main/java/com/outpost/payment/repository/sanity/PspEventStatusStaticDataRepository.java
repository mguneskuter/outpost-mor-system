package com.outpost.payment.repository.sanity;

import com.outpost.payment.PspEventStatuses;
import com.outpost.payment.PspEventStatuses.PspEventStatus;
import com.outpost.payment.repository.PspEventStatusRecord;
import com.outpost.platform.staticdata.StaticDataRepository;
import java.util.List;

final class PspEventStatusStaticDataRepository
    implements StaticDataRepository<PspEventStatuses, PspEventStatus, PspEventStatusRecord> {
  private final PspEventStatusStaticDataMapper mapper;

  PspEventStatusStaticDataRepository(PspEventStatusStaticDataMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Class<PspEventStatuses> staticDataEnum() {
    return PspEventStatuses.class;
  }

  @Override
  public String table() {
    return "psp_event_status";
  }

  @Override
  public PspEventStatus enumValue(PspEventStatuses constant) {
    return constant.getValue();
  }

  @Override
  public PspEventStatusRecord toDatabaseRecord(PspEventStatus value) {
    return new PspEventStatusRecord(value.pspEventStatusId(), value.code());
  }

  @Override
  public PspEventStatus toDomainValue(PspEventStatusRecord record) {
    return java.util.Arrays.stream(PspEventStatuses.values())
        .map(PspEventStatuses::getValue)
        .filter(
            value ->
                value.pspEventStatusId() == record.pspEventStatusId()
                    && value.code().equals(record.code()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "PSP event status record does not match the enum: " + record));
  }

  @Override
  public long id(PspEventStatusRecord record) {
    return record.pspEventStatusId();
  }

  @Override
  public List<PspEventStatusRecord> findAll() {
    return mapper.findAll();
  }
}
