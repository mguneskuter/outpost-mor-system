package com.outpost.account.repository.sanity;

import com.outpost.account.AccountTypes;
import com.outpost.account.AccountTypes.AccountType;
import com.outpost.account.repository.AccountTypeRecord;
import com.outpost.platform.staticdata.StaticDataRepository;
import java.util.List;

/** Typed repository for account types. */
final class AccountTypeStaticDataRepository
    implements StaticDataRepository<AccountTypes, AccountType, AccountTypeRecord> {
  private final AccountTypeStaticDataMapper mapper;

  /** Creates a typed repository. */
  AccountTypeStaticDataRepository(AccountTypeStaticDataMapper mapper) {
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
    return constant.getValue();
  }

  @Override
  public AccountTypeRecord toDatabaseRecord(AccountType value) {
    return new AccountTypeRecord(value.getAccountTypeId(), value.getCode());
  }

  @Override
  public AccountType toDomainValue(AccountTypeRecord record) {
    return AccountTypes.fromCode(record.code())
        .filter(value -> value.getAccountTypeId() == record.accountTypeId())
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
