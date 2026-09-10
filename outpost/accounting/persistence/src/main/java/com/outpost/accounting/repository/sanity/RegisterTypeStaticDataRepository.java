package com.outpost.accounting.repository.sanity;

import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.RegisterTypes.RegisterType;
import com.outpost.accounting.repository.RegisterTypeRecord;
import com.outpost.platform.staticdata.StaticDataRepository;
import java.util.List;

final class RegisterTypeStaticDataRepository
    implements StaticDataRepository<RegisterTypes, RegisterType, RegisterTypeRecord> {
  private final RegisterTypeStaticDataMapper mapper;

  RegisterTypeStaticDataRepository(RegisterTypeStaticDataMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Class<RegisterTypes> staticDataEnum() {
    return RegisterTypes.class;
  }

  @Override
  public String table() {
    return "register_type";
  }

  @Override
  public RegisterType enumValue(RegisterTypes constant) {
    return constant.getValue();
  }

  @Override
  public RegisterTypeRecord toDatabaseRecord(RegisterType value) {
    return new RegisterTypeRecord(value.getRegisterTypeId(), value.getCode());
  }

  @Override
  public RegisterType toDomainValue(RegisterTypeRecord record) {
    return RegisterTypes.fromCode(record.code())
        .filter(v -> v.getRegisterTypeId() == record.registerTypeId())
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Register type record does not match the enum: " + record));
  }

  @Override
  public long id(RegisterTypeRecord record) {
    return record.registerTypeId();
  }

  @Override
  public List<RegisterTypeRecord> findAll() {
    return mapper.findAll();
  }
}
