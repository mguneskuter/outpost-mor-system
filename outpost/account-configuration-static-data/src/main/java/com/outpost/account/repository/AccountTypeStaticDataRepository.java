package com.outpost.account.repository;

import com.outpost.account.AccountTypes;
import com.outpost.account.AccountTypes.AccountType;
import com.outpost.platform.staticdata.StaticDataRepository;
import java.util.List;

/** Typed repository for account types. */
final class AccountTypeStaticDataRepository
    implements StaticDataRepository<AccountTypes, AccountType, AccountTypeRecord> {
  private final AccountTypeStaticDataReadMapper mapper;

  /** Creates a typed repository. */
  AccountTypeStaticDataRepository(AccountTypeStaticDataReadMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Class<AccountTypes> staticDataEnum() {
    return AccountTypes.class;
  }

  @Override
  public String table() {
    return "account_type";
  }

  @Override
  public AccountType enumValue(AccountTypes constant) {
    return constant.value();
  }

  @Override
  public AccountTypeRecord toDatabaseRecord(AccountType value) {
    return new AccountTypeRecord(value.accountTypeId(), value.code());
  }

  @Override
  public AccountType toDomainValue(AccountTypeRecord record) {
    return AccountTypes.fromCode(record.code())
        .filter(value -> value.accountTypeId() == record.accountTypeId())
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Account type record does not match the enum: " + record));
  }

  @Override
  public long id(AccountTypeRecord record) {
    return record.accountTypeId();
  }

  @Override
  public List<AccountTypeRecord> findAll() {
    return mapper.findAll();
  }
}
