package com.outpost.accounting.repository.sanity;

import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionEventTypes.TransactionEventType;
import com.outpost.accounting.repository.TransactionEventTypeRecord;
import com.outpost.platform.staticdata.StaticDataRepository;
import java.util.List;

final class TransactionEventTypeStaticDataRepository
    implements StaticDataRepository<
        TransactionEventTypes, TransactionEventType, TransactionEventTypeRecord> {
  private final TransactionEventTypeStaticDataMapper mapper;

  TransactionEventTypeStaticDataRepository(TransactionEventTypeStaticDataMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Class<TransactionEventTypes> staticDataEnum() {
    return TransactionEventTypes.class;
  }

  @Override
  public String table() {
    return "transaction_event_type";
  }

  @Override
  public TransactionEventType enumValue(TransactionEventTypes constant) {
    return constant.getValue();
  }

  @Override
  public TransactionEventTypeRecord toDatabaseRecord(TransactionEventType value) {
    return new TransactionEventTypeRecord(
        value.getTransactionEventTypeId(), value.getCode(), value.isRequiresJournalEntry());
  }

  @Override
  public TransactionEventType toDomainValue(TransactionEventTypeRecord record) {
    return TransactionEventTypes.fromCode(record.code())
        .filter(v -> v.getTransactionEventTypeId() == record.transactionEventTypeId())
        .filter(v -> v.isRequiresJournalEntry() == record.requiresJournalEntry())
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Transaction event type record does not match the enum: " + record));
  }

  @Override
  public long id(TransactionEventTypeRecord record) {
    return record.transactionEventTypeId();
  }

  @Override
  public List<TransactionEventTypeRecord> findAll() {
    return mapper.findAll();
  }
}
