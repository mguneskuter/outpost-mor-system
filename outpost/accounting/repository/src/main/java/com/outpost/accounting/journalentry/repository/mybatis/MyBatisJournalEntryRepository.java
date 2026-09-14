package com.outpost.accounting.journalentry.repository.mybatis;

import com.outpost.account.Account;
import com.outpost.account.repository.AccountRepository;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.journalentry.JournalEntry;
import com.outpost.accounting.journalentry.JournalEntryLine;
import com.outpost.accounting.journalentry.repository.JournalEntryRepository;
import com.outpost.accounting.transaction.Transaction;
import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.ibatis.session.SqlSession;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Stores journal entries in {@code journal_entry} and {@code journal_entry_line}. */
public final class MyBatisJournalEntryRepository implements JournalEntryRepository {
  private final JournalEntryMapper mapper;
  private final TransactionTemplate transactionTemplate;
  private final AccountRepository accounts;

  /**
   * Creates a repository over a Spring-managed {@code sqlSession} whose inserts run in one
   * transaction, joining the caller's transaction when one is active; the accounts holding a read
   * register are read through {@code accounts}.
   */
  public MyBatisJournalEntryRepository(
      SqlSession sqlSession,
      PlatformTransactionManager transactionManager,
      AccountRepository accounts) {
    this.mapper = sqlSession.getMapper(JournalEntryMapper.class);
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.accounts = accounts;
  }

  /**
   * {@inheritDoc}
   *
   * <p>The entry is stored booked and posted at the time of the database transaction that stores
   * it.
   */
  @Override
  public JournalEntry insertJournalEntry(JournalEntry journalEntry) {
    return Objects.requireNonNull(
        transactionTemplate.execute(status -> insertEntryAndLines(journalEntry)));
  }

  @Override
  public Optional<com.outpost.accounting.journalentry.PendingFee> findPendingFeeByPayment(
      Transaction payment) {
    return Optional.ofNullable(mapper.findPendingFeeByPayment(storedId(payment)))
        .map(
            stored ->
                new com.outpost.accounting.journalentry.PendingFee(
                    new Amount(
                        Currencies.fromCurrencyCode(stored.currencyCode())
                            .orElseThrow(
                                () ->
                                    new IllegalStateException("Stored currency is not supported")),
                        stored.fee()),
                    register(
                        stored.merchantPendingFeeRegisterId(),
                        stored.merchantAccountId(),
                        registerType(stored.merchantRegisterType())),
                    register(
                        stored.platformPendingFeeRegisterId(),
                        stored.platformAccountId(),
                        registerType(stored.platformRegisterType()))));
  }

  @Override
  public Optional<com.outpost.accounting.journalentry.CaptureRegisters>
      findCaptureRegistersByPayment(Transaction payment) {
    return Optional.ofNullable(mapper.findCaptureRegistersByPayment(storedId(payment)))
        .map(
            stored ->
                new com.outpost.accounting.journalentry.CaptureRegisters(
                    register(
                        stored.pspReceivableRegisterId(),
                        stored.pspAccountId(),
                        RegisterTypes.PSP_RECEIVABLE.getValue()),
                    register(
                        stored.taxPayableRegisterId(),
                        stored.taxAuthorityAccountId(),
                        RegisterTypes.TAX_PAYABLE.getValue()),
                    register(
                        stored.merchantPayableRegisterId(),
                        stored.merchantAccountId(),
                        RegisterTypes.MERCHANT_PAYABLE.getValue()),
                    register(
                        stored.feeRevenueRegisterId(),
                        stored.platformAccountId(),
                        RegisterTypes.FEE_REVENUE.getValue())));
  }

  private JournalEntry insertEntryAndLines(JournalEntry journalEntry) {
    long journalEntryId = mapper.insertJournalEntry(journalEntry);
    List<Long> journalEntryLineIds = new ArrayList<>();
    for (JournalEntryLine line : journalEntry.getJournalEntryLines()) {
      journalEntryLineIds.add(mapper.insertJournalEntryLine(journalEntryId, line));
    }
    return journalEntry.withIds(journalEntryId, journalEntryLineIds);
  }

  private Register register(
      long registerId, long accountId, RegisterTypes.RegisterType registerType) {
    Account account =
        accounts
            .findAccountById(accountId)
            .orElseThrow(() -> new IllegalStateException("Stored account is missing"));
    return new Register(registerId, account, registerType);
  }

  private static RegisterTypes.RegisterType registerType(String code) {
    return RegisterTypes.fromCode(code)
        .orElseThrow(() -> new IllegalStateException("Stored register type is not supported"));
  }

  private static long storedId(Transaction payment) {
    return payment
        .getTransactionId()
        .orElseThrow(() -> new IllegalArgumentException("Payment is not stored"));
  }
}
