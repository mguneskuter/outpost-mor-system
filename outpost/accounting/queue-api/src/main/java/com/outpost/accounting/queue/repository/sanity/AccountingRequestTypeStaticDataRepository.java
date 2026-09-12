package com.outpost.accounting.queue.repository.sanity;

import com.outpost.accounting.queue.AccountingRequestTypes;
import com.outpost.accounting.queue.AccountingRequestTypes.AccountingRequestType;
import com.outpost.accounting.queue.repository.AccountingRequestTypeRecord;
import com.outpost.platform.staticdata.StaticDataRepository;
import java.util.Arrays;
import java.util.List;

final class AccountingRequestTypeStaticDataRepository
    implements StaticDataRepository<
        AccountingRequestTypes, AccountingRequestType, AccountingRequestTypeRecord> {
  private final AccountingRequestTypeStaticDataMapper mapper;

  AccountingRequestTypeStaticDataRepository(AccountingRequestTypeStaticDataMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Class<AccountingRequestTypes> staticDataEnum() {
    return AccountingRequestTypes.class;
  }

  @Override
  public String table() {
    return "accounting_request_type";
  }

  @Override
  public AccountingRequestType enumValue(AccountingRequestTypes constant) {
    return constant.getValue();
  }

  @Override
  public AccountingRequestTypeRecord toDatabaseRecord(AccountingRequestType value) {
    return new AccountingRequestTypeRecord(value.accountingRequestTypeId(), value.code());
  }

  @Override
  public AccountingRequestType toDomainValue(AccountingRequestTypeRecord record) {
    return Arrays.stream(AccountingRequestTypes.values())
        .map(AccountingRequestTypes::getValue)
        .filter(value -> value.accountingRequestTypeId() == record.accountingRequestTypeId())
        .filter(value -> value.code().equals(record.code()))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Invalid accounting request type: " + record));
  }

  @Override
  public long id(AccountingRequestTypeRecord record) {
    return record.accountingRequestTypeId();
  }

  @Override
  public List<AccountingRequestTypeRecord> findAll() {
    return mapper.findAll();
  }
}
