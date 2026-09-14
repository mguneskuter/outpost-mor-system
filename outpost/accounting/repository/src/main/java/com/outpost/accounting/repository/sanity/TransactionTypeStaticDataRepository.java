package com.outpost.accounting.repository.sanity;

import com.outpost.accounting.TransactionTypes;
import com.outpost.accounting.TransactionTypes.TransactionType;
import com.outpost.accounting.repository.TransactionTypeRecord;
import com.outpost.platform.staticdata.StaticDataRepository;
import java.util.List;

final class TransactionTypeStaticDataRepository
    implements StaticDataRepository<TransactionTypes, TransactionType, TransactionTypeRecord> {
  private final TransactionTypeStaticDataMapper mapper;

  TransactionTypeStaticDataRepository(TransactionTypeStaticDataMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Class<TransactionTypes> staticDataEnum() {
    return TransactionTypes.class;
  }

  @Override
  public String table() {
    return "transaction_type";
  }

  @Override
  public TransactionType enumValue(TransactionTypes constant) {
    return constant.getValue();
  }

  @Override
  public TransactionTypeRecord toDatabaseRecord(TransactionType value) {
    return new TransactionTypeRecord(value.getTransactionTypeId(), value.getCode());
  }

  @Override
  public TransactionType toDomainValue(TransactionTypeRecord record) {
    return TransactionTypes.fromCode(record.code())
        .filter(v -> v.getTransactionTypeId() == record.transactionTypeId())
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Transaction type record does not match the enum: " + record));
  }

  @Override
  public long id(TransactionTypeRecord record) {
    return record.transactionTypeId();
  }

  @Override
  public List<TransactionTypeRecord> findAll() {
    return mapper.findAll();
  }
}
