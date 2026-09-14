package com.outpost.accounting.repository.sanity;

import com.outpost.accounting.AccountTypeRegisterTypes;
import com.outpost.accounting.AccountTypeRegisterTypes.AccountTypeRegisterType;
import com.outpost.accounting.repository.AccountTypeRegisterTypeRecord;
import com.outpost.platform.staticdata.StaticDataRepository;
import java.util.Arrays;
import java.util.List;

final class AccountTypeRegisterTypeStaticDataRepository
    implements StaticDataRepository<
        AccountTypeRegisterTypes, AccountTypeRegisterType, AccountTypeRegisterTypeRecord> {
  private final AccountTypeRegisterTypeStaticDataMapper mapper;

  AccountTypeRegisterTypeStaticDataRepository(AccountTypeRegisterTypeStaticDataMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Class<AccountTypeRegisterTypes> staticDataEnum() {
    return AccountTypeRegisterTypes.class;
  }

  @Override
  public String table() {
    return "account_type_register_type";
  }

  @Override
  public AccountTypeRegisterType enumValue(AccountTypeRegisterTypes constant) {
    return constant.getValue();
  }

  @Override
  public AccountTypeRegisterTypeRecord toDatabaseRecord(AccountTypeRegisterType value) {
    return new AccountTypeRegisterTypeRecord(
        value.getAccountTypeRegisterTypeId(),
        value.getAccountType().getAccountTypeId(),
        value.getRegisterType().getRegisterTypeId());
  }

  @Override
  public AccountTypeRegisterType toDomainValue(AccountTypeRegisterTypeRecord record) {
    return Arrays.stream(AccountTypeRegisterTypes.values())
        .map(AccountTypeRegisterTypes::getValue)
        .filter(
            value ->
                value.getAccountTypeRegisterTypeId() == record.accountTypeRegisterTypeId()
                    && value.getAccountType().getAccountTypeId() == record.accountTypeId()
                    && value.getRegisterType().getRegisterTypeId() == record.registerTypeId())
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Account type register type record does not match the enum: " + record));
  }

  @Override
  public long id(AccountTypeRegisterTypeRecord record) {
    return record.accountTypeRegisterTypeId();
  }

  @Override
  public List<AccountTypeRegisterTypeRecord> findAll() {
    return mapper.findAll();
  }
}
