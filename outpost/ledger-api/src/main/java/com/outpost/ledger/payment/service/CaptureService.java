package com.outpost.ledger.payment.service;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.account.repository.AccountRepository;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionEventTypes.TransactionEventType;
import com.outpost.accounting.TransactionTypes;
import com.outpost.accounting.journalentry.CaptureRegisters;
import com.outpost.accounting.journalentry.JournalEntry;
import com.outpost.accounting.journalentry.PendingFee;
import com.outpost.accounting.journalentry.repository.JournalEntryRepository;
import com.outpost.accounting.payment.PaymentStateMachine;
import com.outpost.accounting.repository.RegisterRepository;
import com.outpost.accounting.templates.CaptureJournalTemplates;
import com.outpost.accounting.templates.PendingFeeJournalTemplates;
import com.outpost.accounting.transaction.PaymentDetail;
import com.outpost.accounting.transaction.Transaction;
import com.outpost.accounting.transaction.TransactionEvent;
import com.outpost.accounting.transaction.repository.TransactionRepository;
import com.outpost.framework.logging.LogFields;
import com.outpost.framework.logging.StructuredLogField;
import com.outpost.framework.logging.StructuredLogger;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/** Books the PSP's capture answer on an authorised payment and its journal entry. */
public class CaptureService {
  private static final StructuredLogger LOGGER =
      new StructuredLogger(LoggerFactory.getLogger(CaptureService.class));
  private final TransactionRepository transactions;
  private final JournalEntryRepository journalEntries;
  private final AccountRepository accounts;
  private final RegisterRepository registers;
  private final PaymentStateMachine stateMachine;

  /** Creates a service over the repositories it books through and the payment state machine. */
  public CaptureService(
      TransactionRepository transactions,
      JournalEntryRepository journalEntries,
      AccountRepository accounts,
      RegisterRepository registers,
      PaymentStateMachine stateMachine) {
    this.transactions = transactions;
    this.journalEntries = journalEntries;
    this.accounts = accounts;
    this.registers = registers;
    this.stateMachine = stateMachine;
  }

  /**
   * Books the PSP's capture answer as the payment's single CAPTURE transaction for the payment's
   * gross amount: CAPTURED and the CAPTURE entry when {@code success}, otherwise CAPTURE_FAILED and
   * the release of the pending fee. A repeat of the booked answer writes nothing.
   *
   * @throws BookingException when the request is not booked; its code says why
   */
  @Transactional
  public void bookCapture(String originalReference, boolean success) {
    TransactionEventType eventType =
        success
            ? TransactionEventTypes.CAPTURED.getValue()
            : TransactionEventTypes.CAPTURE_FAILED.getValue();
    PaymentDetail payment =
        transactions
            .findPaymentDetailByReferenceForUpdate(originalReference)
            .orElseThrow(() -> refused(BookingErrorCodes.PAYMENT_NOT_FOUND));
    Transaction paymentTransaction = payment.getPaymentTransaction();
    Optional<TransactionEvent> bookedCaptureEvent =
        transactions.findCaptureTransactionEventByPayment(paymentTransaction);
    if (bookedCaptureEvent.isPresent()) {
      if (bookedCaptureEvent.get().getTransactionEventType().equals(eventType)) {
        LOGGER.info(
            "Capture already booked",
            new StructuredLogField(LogFields.ORIGINAL_REFERENCE, originalReference),
            new StructuredLogField(LogFields.EVENT, eventType.getCode()));
        return;
      }
      throw refused(BookingErrorCodes.CAPTURE_CONFLICT);
    }
    TransactionEventType paymentState;
    try {
      paymentState =
          stateMachine.fold(
              transactions.findTransactionEvents(paymentTransaction).stream()
                  .map(TransactionEvent::getTransactionEventType)
                  .toList());
    } catch (IllegalArgumentException exception) {
      throw inconsistentBooking(exception);
    }
    if (!stateMachine.isNextCapture(paymentState, false, eventType)) {
      throw refused(BookingErrorCodes.INVALID_CAPTURE);
    }
    PendingFee pendingFee =
        journalEntries
            .findPendingFeeByPayment(paymentTransaction)
            .orElseThrow(() -> inconsistentBooking(null));

    String captureReference = "capture-" + UUID.randomUUID();
    Transaction capture =
        transactions
            .insertTransaction(
                Transaction.childOf(
                    paymentTransaction,
                    null,
                    TransactionTypes.CAPTURE.getValue(),
                    paymentTransaction.getMerchantAccount(),
                    captureReference,
                    paymentTransaction.getAmount(),
                    null))
            .orElseThrow(() -> refused(BookingErrorCodes.REFERENCE_CONFLICT));
    TransactionEvent event =
        transactions
            .insertTransactionEvent(capture, eventType)
            .orElseThrow(() -> inconsistentBooking(null));
    journalEntries.insertJournalEntry(journalEntry(event, payment, pendingFee));
    LOGGER.info(
        success
            ? "Capture booked: net to the merchant, tax to the tax authority, fee to Outpost"
            : "Capture failure booked and the pending fee released",
        new StructuredLogField(LogFields.ORIGINAL_REFERENCE, originalReference),
        new StructuredLogField(LogFields.CAPTURE_REFERENCE, captureReference),
        new StructuredLogField(LogFields.EVENT, eventType.getCode()));
  }

  private JournalEntry journalEntry(
      TransactionEvent event, PaymentDetail payment, PendingFee pendingFee) {
    try {
      if (!event.getTransactionEventType().equals(TransactionEventTypes.CAPTURED.getValue())) {
        return PendingFeeJournalTemplates.FEE_RELEASE.build(
            event,
            pendingFee.merchantPendingFeeRegister(),
            pendingFee.platformPendingFeeRegister(),
            pendingFee.fee(),
            event.getOccurredAt());
      }
      return CaptureJournalTemplates.CAPTURE.build(
          event, payment, captureRegisters(payment), pendingFee, event.getOccurredAt());
    } catch (IllegalArgumentException | ArithmeticException exception) {
      throw inconsistentBooking(exception);
    }
  }

  private CaptureRegisters captureRegisters(PaymentDetail payment) {
    Account taxAuthority =
        accounts
            .findTaxAuthorityAccountByCountryId(payment.getShopperCountry().getCountryId())
            .orElseThrow(() -> inconsistentBooking(null));
    Account platform =
        accounts
            .findAccountByAccountType(AccountTypes.PLATFORM.getValue())
            .orElseThrow(() -> inconsistentBooking(null));
    return new CaptureRegisters(
        register(payment.getPspAccount(), RegisterTypes.PSP_RECEIVABLE),
        register(taxAuthority, RegisterTypes.TAX_PAYABLE),
        register(
            payment.getPaymentTransaction().getMerchantAccount(), RegisterTypes.MERCHANT_PAYABLE),
        register(platform, RegisterTypes.FEE_REVENUE));
  }

  private Register register(Account account, RegisterTypes registerType) {
    return registers
        .findRegisterByAccountAndRegisterType(account, registerType.getValue())
        .orElseThrow(() -> inconsistentBooking(null));
  }

  private static BookingException refused(BookingErrorCodes code) {
    return new BookingException(code, null);
  }

  private static BookingException inconsistentBooking(@Nullable Throwable cause) {
    return new BookingException(BookingErrorCodes.INCONSISTENT_BOOKING, cause);
  }
}
