package com.outpost.accounting.queue.repository.sanity;

import com.outpost.accounting.queue.AccountingRequestResults;
import com.outpost.accounting.queue.AccountingRequestResults.AccountingRequestResult;
import com.outpost.accounting.queue.repository.AccountingRequestResultRecord;
import com.outpost.platform.staticdata.StaticDataRepository;
import java.util.Arrays;
import java.util.List;

final class AccountingRequestResultStaticDataRepository
    implements StaticDataRepository<
        AccountingRequestResults, AccountingRequestResult, AccountingRequestResultRecord> {
  private final AccountingRequestResultStaticDataMapper mapper;

  AccountingRequestResultStaticDataRepository(AccountingRequestResultStaticDataMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Class<AccountingRequestResults> staticDataEnum() {
    return AccountingRequestResults.class;
  }

  @Override
  public String table() {
    return "accounting_request_result";
  }

  @Override
  public AccountingRequestResult enumValue(AccountingRequestResults constant) {
    return constant.getValue();
  }

  @Override
  public AccountingRequestResultRecord toDatabaseRecord(AccountingRequestResult value) {
    return new AccountingRequestResultRecord(value.accountingRequestResultId(), value.code());
  }

  @Override
  public AccountingRequestResult toDomainValue(AccountingRequestResultRecord record) {
    return Arrays.stream(AccountingRequestResults.values())
        .map(AccountingRequestResults::getValue)
        .filter(value -> value.accountingRequestResultId() == record.accountingRequestResultId())
        .filter(value -> value.code().equals(record.code()))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Invalid accounting request result: " + record));
  }

  @Override
  public long id(AccountingRequestResultRecord record) {
    return record.accountingRequestResultId();
  }

  @Override
  public List<AccountingRequestResultRecord> findAll() {
    return mapper.findAll();
  }
}
