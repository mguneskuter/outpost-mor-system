package com.outpost.ledger.payment.service;

import com.outpost.account.Account;
import com.outpost.accounting.JournalEntry;
import com.outpost.accounting.RefundDetail;
import com.outpost.accounting.Register;
import com.outpost.accounting.RegisterTypes;
import com.outpost.accounting.Transaction;
import com.outpost.accounting.TransactionEvent;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionTypes;
import com.outpost.accounting.journalentry.repository.JournalEntryRepository;
import com.outpost.accounting.templates.RefundJournalTemplates;
import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.ledger.payment.repository.CapturePosting;
import com.outpost.ledger.payment.repository.ExistingPayment;
import com.outpost.ledger.payment.repository.PaymentEvent;
import com.outpost.ledger.payment.repository.PaymentRepository;
import com.outpost.ledger.payment.repository.PaymentTransaction;
import com.outpost.ledger.payment.repository.RefundChild;
import com.outpost.ledger.payment.repository.StoredTransaction;
import com.outpost.payment.common.Amount;
import java.util.Arrays;
import org.springframework.transaction.annotation.Transactional;

/** Books a PSP-confirmed full refund of a captured payment. */
public class RefundService {
  private final PaymentRepository repository;
  private final JournalEntryRepository journalEntryRepository;

  /** Creates a service using the persistence seams. */
  public RefundService(
      PaymentRepository repository, JournalEntryRepository journalEntryRepository) {
    this.repository = repository;
    this.journalEntryRepository = journalEntryRepository;
  }

  /**
   * Books the full refund confirmed by the PSP under {@code refundReference}: the REFUND
   * transaction for the payment's gross, its refund detail for the payment's net and tax, the
   * REFUNDED event, and the REFUND entry. A repeat of a booked refund writes nothing.
   *
   * @throws RefundException 404 PAYMENT_NOT_FOUND, 409 REFERENCE_CONFLICT, 422 NOT_CAPTURED, 422
   *     ALREADY_REFUNDED
   */
  @Transactional
  public void refund(String originalReference, String refundReference) {
    long refunded = TransactionEventTypes.REFUNDED.getValue().getTransactionEventTypeId();
    PaymentTransaction payment = repository.findPaymentTransactionForUpdate(originalReference);
    if (payment == null) {
      throw new RefundException(404, "PAYMENT_NOT_FOUND");
    }
    RefundChild existing = repository.findRefundByReference(refundReference);
    if (existing != null) {
      if (existing.paymentTransactionId() == payment.transactionId()
          && existing.eventTypeId() != null
          && existing.eventTypeId() == refunded) {
        return;
      }
      throw new RefundException(409, "REFERENCE_CONFLICT");
    }
    if (!repository.hasExactlyOneSuccessfulFullCapture(
        payment.transactionId(), payment.grossQuantity(), payment.currencyId())) {
      throw new RefundException(422, "NOT_CAPTURED");
    }
    for (RefundChild child : repository.findRefundChildren(payment.transactionId())) {
      if (child.eventTypeId() != null && child.eventTypeId() == refunded) {
        throw new RefundException(422, "ALREADY_REFUNDED");
      }
    }
    StoredTransaction refund =
        repository.insertRefundTransaction(
            payment.transactionId(),
            payment.merchantAccountId(),
            refundReference,
            payment.grossQuantity(),
            payment.currencyId());
    if (refund == null) {
      throw new RefundException(409, "REFERENCE_CONFLICT");
    }
    repository.insertRefundDetail(
        refund.transactionId(), payment.netQuantity(), payment.taxQuantity());
    PaymentEvent event = repository.insertPaymentEvent(refund.transactionId(), refunded);
    if (event == null) {
      throw new RefundException(500, "INTERNAL_ERROR");
    }
    RefundChild stored = repository.findRefundByReference(refundReference);
    if (stored == null) {
      throw new RefundException(500, "INTERNAL_ERROR");
    }
    appendRefundEntry(payment, originalReference, stored, event);
  }

  private void appendRefundEntry(
      PaymentTransaction payment,
      String originalReference,
      RefundChild refund,
      PaymentEvent event) {
    try {
      CapturePosting posting = repository.findCapturePosting(payment.transactionId());
      Currency currency = currency(payment.currencyId());
      if (posting == null
          || posting.currencyId() != payment.currencyId()
          || refund.currencyId() != payment.currencyId()
          || refund.quantity() != Math.addExact(refund.netQuantity(), refund.taxQuantity())) {
        throw new IllegalArgumentException("capture posting is not compatible with refund");
      }
      Register psp =
          register(posting.pspAccountId(), RegisterTypes.PSP_RECEIVABLE, posting.pspRegisterId());
      Register tax =
          register(
              posting.taxAuthorityAccountId(), RegisterTypes.TAX_PAYABLE, posting.taxRegisterId());
      Register merchant =
          register(
              posting.merchantAccountId(),
              RegisterTypes.MERCHANT_PAYABLE,
              posting.merchantRegisterId());

      Transaction parentTransaction = buildTransaction(payment, originalReference);
      Transaction refundTransaction =
          Transaction.childOf(
              parentTransaction,
              refund.transactionId(),
              TransactionTypes.REFUND.getValue(),
              parentTransaction.getMerchantAccount(),
              refund.reference(),
              new Amount(currency, refund.quantity()),
              refund.createdTs());
      new RefundDetail(
          refundTransaction,
          new Amount(currency, refund.netQuantity()),
          new Amount(currency, refund.taxQuantity()));
      TransactionEvent refundEvent =
          new TransactionEvent(
              event.transactionEventId(),
              refundTransaction,
              TransactionEventTypes.REFUNDED.getValue(),
              event.occurredAt());
      JournalEntry refundEntry =
          RefundJournalTemplates.REFUND.build(
              refundEvent,
              psp,
              tax,
              merchant,
              new Amount(currency, refund.quantity()),
              new Amount(currency, refund.netQuantity()),
              new Amount(currency, refund.taxQuantity()),
              event.occurredAt());
      journalEntryRepository.insertJournalEntry(refundEntry);
    } catch (IllegalArgumentException | ArithmeticException exception) {
      throw new RefundException(500, "INTERNAL_ERROR");
    }
  }

  private Transaction buildTransaction(PaymentTransaction payment, String originalReference) {
    Account merchant = repository.findAccountById(payment.merchantAccountId());
    ExistingPayment created = repository.findByReference(originalReference);
    if (merchant == null || created == null || created.transactionId() != payment.transactionId()) {
      throw new IllegalArgumentException("payment transaction is missing");
    }
    return Transaction.of(
        payment.transactionId(),
        TransactionTypes.PAYMENT.getValue(),
        merchant,
        created.reference(),
        new Amount(currency(payment.currencyId()), payment.grossQuantity()),
        created.createdTs());
  }

  private Register register(long accountId, RegisterTypes type, long expectedRegisterId) {
    Register register = repository.findRegister(accountId, type.getValue().getRegisterTypeId());
    if (register == null
        || register.getAccount().getAccountId() != accountId
        || register.getRegisterId() != expectedRegisterId) {
      throw new IllegalArgumentException("register is missing or differs from the posted one");
    }
    return register;
  }

  private static Currency currency(long currencyId) {
    return Arrays.stream(Currencies.values())
        .map(Currencies::getValue)
        .filter(value -> value.getCurrencyId() == currencyId)
        .findFirst()
        .orElseThrow(IllegalArgumentException::new);
  }
}
