package com.outpost.accounting.queue.repository.sanity;

import com.outpost.accounting.queue.AccountingRequestStatuses;
import com.outpost.accounting.queue.AccountingRequestStatuses.AccountingRequestStatus;
import com.outpost.accounting.queue.repository.AccountingRequestStatusRecord;
import com.outpost.platform.staticdata.StaticDataRepository;
import java.util.Arrays;
import java.util.List;

final class AccountingRequestStatusStaticDataRepository
    implements StaticDataRepository<
        AccountingRequestStatuses, AccountingRequestStatus, AccountingRequestStatusRecord> {
  private final AccountingRequestStatusStaticDataMapper mapper;

  AccountingRequestStatusStaticDataRepository(AccountingRequestStatusStaticDataMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Class<AccountingRequestStatuses> staticDataEnum() {
    return AccountingRequestStatuses.class;
  }

  @Override
  public String table() {
    return "accounting_request_status_type";
  }

  @Override
  public AccountingRequestStatus enumValue(AccountingRequestStatuses constant) {
    return constant.getValue();
  }

  @Override
  public AccountingRequestStatusRecord toDatabaseRecord(AccountingRequestStatus value) {
    return new AccountingRequestStatusRecord(value.accountingRequestStatusId(), value.code());
  }

  @Override
  public AccountingRequestStatus toDomainValue(AccountingRequestStatusRecord record) {
    return Arrays.stream(AccountingRequestStatuses.values())
        .map(AccountingRequestStatuses::getValue)
        .filter(value -> value.accountingRequestStatusId() == record.accountingRequestStatusId())
        .filter(value -> value.code().equals(record.code()))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Invalid accounting request status: " + record));
  }

  @Override
  public long id(AccountingRequestStatusRecord record) {
    return record.accountingRequestStatusId();
  }

  @Override
  public List<AccountingRequestStatusRecord> findAll() {
    return mapper.findAll();
  }
}
