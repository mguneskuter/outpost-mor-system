package com.outpost.ledger.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.account.configuration.FeeModes;
import com.outpost.account.configuration.MerchantFeeConfiguration;
import com.outpost.accounting.JournalEntry;
import com.outpost.accounting.JournalEntryLine;
import com.outpost.accounting.JournalEntryTypes;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.api.CreatePaymentRequest;
import com.outpost.accounting.journalentry.repository.JournalEntryRepository;
import com.outpost.accounting.payment.PaymentFeeCalculator;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Currencies;
import com.outpost.ledger.payment.repository.PaymentEvent;
import com.outpost.ledger.payment.repository.PaymentRepository;
import com.outpost.ledger.payment.repository.StoredTransaction;
import com.outpost.payment.common.Amount;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PaymentCreationServiceTest {
  private static final Instant NOW = Instant.parse("2026-09-13T10:00:00Z");
  private static final long EUR = Currencies.EUR.getValue().getCurrencyId();
  private static final long PENDING_FEE = RegisterTypes.PENDING_FEE.getValue().getRegisterTypeId();
  private static final long TRANSACTION_ID = 10L;
  private static final long EVENT_ID = 11L;

  private final PaymentRepository repository = mock(PaymentRepository.class);
  private final JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
  private final Account root =
      Account.of(1L, AccountTypes.ROOT.getValue(), "ROOT", "Root", true, NOW, null);
  private final Account platform =
      Account.of(100L, AccountTypes.PLATFORM.getValue(), "OUTPOST", "Outpost", true, NOW, root);
  private final Account merchant =
      Account.of(200L, AccountTypes.MERCHANT.getValue(), "SHOP", "Shop", true, NOW, root);
  private final Account otherMerchant =
      Account.of(210L, AccountTypes.MERCHANT.getValue(), "OTHER", "Other", true, NOW, root);
  private final Account psp =
      Account.of(300L, AccountTypes.PSP.getValue(), "PSP", "PSP", true, NOW, root);
  private final Account taxAuthority =
      Account.of(1006L, AccountTypes.TAX_AUTHORITY.getValue(), "TAX_DE", "Tax DE", true, NOW, root);
  private final Register merchantPendingFee =
      new Register(20008L, merchant, RegisterTypes.PENDING_FEE.getValue());
  private final Register platformPendingFee =
      new Register(10008L, platform, RegisterTypes.PENDING_FEE.getValue());
  private final PaymentCreationService service =
      new PaymentCreationService(repository, journalEntryRepository, new PaymentFeeCalculator());

  @Test
  void booksPendingFeeAsFivePercentOfNetNotGross() {
    arrangePayment(percentage(500));

    service.create(request(10_000L, 2_000L));

    // 5% of net 10000; 5% of gross 12000 would be 600.
    assertPendingFeeLines(500L);
  }

  @Test
  void booksZeroPendingFeeLinesWhenTheRateIsZero() {
    arrangePayment(percentage(0));

    service.create(request(10_000L, 2_000L));

    assertPendingFeeLines(0L);
  }

  @Test
  void booksPendingFeeEqualToNetWhenTheFixedFeeConsumesIt() {
    arrangePayment(fixed(10_000L));

    service.create(request(10_000L, 2_000L));

    assertPendingFeeLines(10_000L);
  }

  @Test
  void rejectsFeeAboveNetBeforeWritingAnything() {
    arrangePayment(fixed(10_001L));

    PaymentCreationException failure =
        catchThrowableOfType(
            PaymentCreationException.class, () -> service.create(request(10_000L, 2_000L)));

    assertThat(failure.code()).isEqualTo("FEE_ABOVE_NET");
    verify(repository, never()).insertTransaction(anyLong(), any(), anyLong(), anyLong());
    verify(journalEntryRepository, never()).insertJournalEntry(any());
  }

  @Test
  void rejectsMerchantPendingFeeRegisterOfAnotherMerchantBeforeJournalWrites() {
    arrangePayment(percentage(500));
    when(repository.findRegister(merchant.getAccountId(), PENDING_FEE))
        .thenReturn(new Register(21008L, otherMerchant, RegisterTypes.PENDING_FEE.getValue()));

    assertInternalErrorWithoutJournalWrites();
  }

  @Test
  void rejectsPlatformPendingFeeRegisterOfNonPlatformAccountBeforeJournalWrites() {
    arrangePayment(percentage(500));
    when(repository.findRegister(platform.getAccountId(), PENDING_FEE))
        .thenReturn(new Register(30008L, psp, RegisterTypes.PENDING_FEE.getValue()));

    assertInternalErrorWithoutJournalWrites();
  }

  @Test
  void rejectsMerchantRegisterOfTheWrongRegisterTypeBeforeJournalWrites() {
    arrangePayment(percentage(500));
    when(repository.findRegister(merchant.getAccountId(), PENDING_FEE))
        .thenReturn(new Register(20001L, merchant, RegisterTypes.MERCHANT_PAYABLE.getValue()));

    assertInternalErrorWithoutJournalWrites();
  }

  private void assertPendingFeeLines(long fee) {
    ArgumentCaptor<JournalEntry> stored = ArgumentCaptor.forClass(JournalEntry.class);
    verify(journalEntryRepository).insertJournalEntry(stored.capture());
    assertThat(stored.getValue().getJournalEntryType())
        .isEqualTo(JournalEntryTypes.FEE_PENDING.getValue());
    assertThat(stored.getValue().getJournalEntryLines())
        .extracting(JournalEntryLine::getRegister, line -> line.getAmount().quantity())
        .containsExactlyInAnyOrder(tuple(merchantPendingFee, fee), tuple(platformPendingFee, -fee));
  }

  private void assertInternalErrorWithoutJournalWrites() {
    PaymentCreationException failure =
        catchThrowableOfType(
            PaymentCreationException.class, () -> service.create(request(10_000L, 2_000L)));

    assertThat(failure.status()).isEqualTo(500);
    assertThat(failure.code()).isEqualTo("INTERNAL_ERROR");
    verify(journalEntryRepository, never()).insertJournalEntry(any());
  }

  private void arrangePayment(MerchantFeeConfiguration feeConfiguration) {
    when(repository.findAccount(merchant.getCode())).thenReturn(merchant);
    when(repository.findAccount(psp.getCode())).thenReturn(psp);
    when(repository.findFee(merchant.getAccountId(), EUR)).thenReturn(feeConfiguration);
    when(repository.insertTransaction(merchant.getAccountId(), "payment-1", 12_000L, EUR))
        .thenReturn(new StoredTransaction(TRANSACTION_ID, NOW));
    when(repository.findTaxAuthorityAccountByCountryId(Countries.GERMANY.getValue().getCountryId()))
        .thenReturn(taxAuthority);
    when(repository.findPlatformAccount()).thenReturn(platform);
    when(repository.findRegister(merchant.getAccountId(), PENDING_FEE))
        .thenReturn(merchantPendingFee);
    when(repository.findRegister(platform.getAccountId(), PENDING_FEE))
        .thenReturn(platformPendingFee);
    when(repository.insertEvent(TRANSACTION_ID))
        .thenReturn(
            new PaymentEvent(
                EVENT_ID,
                TransactionEventTypes.ORDER_CREATED.getValue().getTransactionEventTypeId(),
                NOW));
  }

  private MerchantFeeConfiguration percentage(int feeRateBps) {
    return new MerchantFeeConfiguration(
        1L, merchant, Currencies.EUR.getValue(), FeeModes.PERCENTAGE.getValue(), feeRateBps, null);
  }

  private MerchantFeeConfiguration fixed(long feeFixed) {
    return new MerchantFeeConfiguration(
        1L,
        merchant,
        Currencies.EUR.getValue(),
        FeeModes.PERCENTAGE_PLUS_FIXED.getValue(),
        0,
        new Amount(Currencies.EUR.getValue(), feeFixed));
  }

  private CreatePaymentRequest request(long net, long tax) {
    return new CreatePaymentRequest(
        "payment-1", merchant.getCode(), psp.getCode(), "DE", null, net, tax, net + tax, "EUR");
  }
}
