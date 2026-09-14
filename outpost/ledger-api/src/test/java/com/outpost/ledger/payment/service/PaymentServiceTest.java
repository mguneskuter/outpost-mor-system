package com.outpost.ledger.payment.service;

import static com.outpost.ledger.payment.service.BookingFixtures.eur;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outpost.account.AccountTypes;
import com.outpost.account.configuration.FeeModes;
import com.outpost.account.configuration.MerchantFeeConfiguration;
import com.outpost.account.configuration.repository.MerchantFeeConfigurationRepository;
import com.outpost.account.repository.AccountRepository;
import com.outpost.accounting.JournalEntryTypes;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.accounting.api.AccountingQueueRequestTypes;
import com.outpost.accounting.journalentry.JournalEntry;
import com.outpost.accounting.journalentry.JournalEntryLine;
import com.outpost.accounting.journalentry.repository.JournalEntryRepository;
import com.outpost.accounting.payment.PaymentFeeCalculator;
import com.outpost.accounting.repository.RegisterRepository;
import com.outpost.accounting.transaction.Transaction;
import com.outpost.accounting.transaction.repository.TransactionRepository;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Currencies;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PaymentServiceTest {
  private final BookingFixtures fixtures = new BookingFixtures();
  private final TransactionRepository transactions = mock(TransactionRepository.class);
  private final JournalEntryRepository journalEntries = mock(JournalEntryRepository.class);
  private final AccountRepository accounts = mock(AccountRepository.class);
  private final RegisterRepository registers = mock(RegisterRepository.class);
  private final MerchantFeeConfigurationRepository feeConfigurations =
      mock(MerchantFeeConfigurationRepository.class);
  private final PaymentService service =
      new PaymentService(
          transactions,
          journalEntries,
          accounts,
          registers,
          feeConfigurations,
          new PaymentFeeCalculator());

  @Test
  void booksPendingFeeAsFivePercentOfNetNotGross() {
    arrangePayment(percentage(500));

    service.bookPayment(request());

    // 5% of net 10000; 5% of gross 12000 would be 600.
    assertPendingFeeLines(500L);
  }

  @Test
  void booksZeroPendingFeeLinesWhenTheRateIsZero() {
    arrangePayment(percentage(0));

    service.bookPayment(request());

    assertPendingFeeLines(0L);
  }

  @Test
  void booksPendingFeeEqualToNetWhenTheFixedFeeConsumesIt() {
    arrangePayment(fixed(10_000L));

    service.bookPayment(request());

    assertPendingFeeLines(10_000L);
  }

  @Test
  void rejectsFeeAboveNetBeforeWritingAnything() {
    arrangePayment(fixed(10_001L));

    BookingException failure =
        catchThrowableOfType(BookingException.class, () -> service.bookPayment(request()));

    assertThat(failure.code()).isEqualTo(BookingErrorCodes.FEE_ABOVE_NET);
    verify(transactions, never()).insertPaymentDetail(any());
    verify(journalEntries, never()).insertJournalEntry(any());
  }

  @Test
  void rejectsMerchantPendingFeeRegisterOfAnotherMerchantBeforeJournalWrites() {
    arrangePayment(percentage(500));
    stubPendingFeeRegister(
        fixtures.merchant,
        new Register(21008L, fixtures.otherMerchant, RegisterTypes.PENDING_FEE.getValue()));

    assertInconsistentBookingWithoutJournalWrites();
  }

  @Test
  void rejectsPlatformPendingFeeRegisterOfNonPlatformAccountBeforeJournalWrites() {
    arrangePayment(percentage(500));
    stubPendingFeeRegister(
        fixtures.platform,
        new Register(30008L, fixtures.psp, RegisterTypes.PENDING_FEE.getValue()));

    assertInconsistentBookingWithoutJournalWrites();
  }

  @Test
  void rejectsMerchantRegisterOfTheWrongRegisterTypeBeforeJournalWrites() {
    arrangePayment(percentage(500));
    stubPendingFeeRegister(
        fixtures.merchant,
        new Register(20001L, fixtures.merchant, RegisterTypes.MERCHANT_PAYABLE.getValue()));

    assertInconsistentBookingWithoutJournalWrites();
  }

  private void assertPendingFeeLines(long fee) {
    ArgumentCaptor<JournalEntry> stored = ArgumentCaptor.forClass(JournalEntry.class);
    verify(journalEntries).insertJournalEntry(stored.capture());
    assertThat(stored.getValue().getJournalEntryType())
        .isEqualTo(JournalEntryTypes.FEE_PENDING.getValue());
    assertThat(stored.getValue().getJournalEntryLines())
        .extracting(JournalEntryLine::getRegister, line -> line.getAmount().quantity())
        .containsExactlyInAnyOrder(
            tuple(fixtures.merchantPendingFee, fee), tuple(fixtures.platformPendingFee, -fee));
  }

  private void assertInconsistentBookingWithoutJournalWrites() {
    BookingException failure =
        catchThrowableOfType(BookingException.class, () -> service.bookPayment(request()));
    assertThat(failure.code()).isEqualTo(BookingErrorCodes.INCONSISTENT_BOOKING);
    verify(journalEntries, never()).insertJournalEntry(any());
  }

  private void arrangePayment(MerchantFeeConfiguration feeConfiguration) {
    when(accounts.findAccountByCode(fixtures.merchant.getCode()))
        .thenReturn(Optional.of(fixtures.merchant));
    when(accounts.findAccountByCode(fixtures.psp.getCode())).thenReturn(Optional.of(fixtures.psp));
    when(feeConfigurations.findMerchantFeeConfigurationByAccountAndCurrency(
            fixtures.merchant, Currencies.EUR.getValue()))
        .thenReturn(Optional.of(feeConfiguration));
    when(transactions.insertPaymentDetail(any())).thenReturn(Optional.of(fixtures.payment()));
    when(accounts.findTaxAuthorityAccountByCountryId(Countries.GERMANY.getValue().getCountryId()))
        .thenReturn(Optional.of(fixtures.taxAuthority));
    when(accounts.findAccountByAccountType(AccountTypes.PLATFORM.getValue()))
        .thenReturn(Optional.of(fixtures.platform));
    stubPendingFeeRegister(fixtures.merchant, fixtures.merchantPendingFee);
    stubPendingFeeRegister(fixtures.platform, fixtures.platformPendingFee);
    when(transactions.insertTransactionEvent(
            any(), eq(TransactionEventTypes.ORDER_CREATED.getValue())))
        .thenAnswer(
            invocation ->
                Optional.of(
                    BookingFixtures.event(
                        11L,
                        invocation.<Transaction>getArgument(0),
                        TransactionEventTypes.ORDER_CREATED)));
  }

  private void stubPendingFeeRegister(com.outpost.account.Account account, Register register) {
    when(registers.findRegisterByAccountAndRegisterType(
            account, RegisterTypes.PENDING_FEE.getValue()))
        .thenReturn(Optional.of(register));
  }

  private MerchantFeeConfiguration percentage(int feeRateBps) {
    return new MerchantFeeConfiguration(
        1L,
        fixtures.merchant,
        Currencies.EUR.getValue(),
        FeeModes.PERCENTAGE.getValue(),
        feeRateBps,
        null);
  }

  private MerchantFeeConfiguration fixed(long feeFixed) {
    return new MerchantFeeConfiguration(
        1L,
        fixtures.merchant,
        Currencies.EUR.getValue(),
        FeeModes.PERCENTAGE_PLUS_FIXED.getValue(),
        0,
        eur(feeFixed));
  }

  private AccountingQueueRequest request() {
    return new AccountingQueueRequest(
        AccountingQueueRequestTypes.ORDER_CREATED,
        BookingFixtures.PAYMENT_REFERENCE,
        "merchant-order-1",
        fixtures.psp.getCode(),
        "41",
        null,
        null,
        fixtures.merchant.getCode(),
        Countries.GERMANY.getValue(),
        null,
        eur(10_000L),
        eur(2_000L),
        eur(12_000L));
  }
}
