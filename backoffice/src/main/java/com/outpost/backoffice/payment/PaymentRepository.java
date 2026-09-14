package com.outpost.backoffice.payment;

import java.util.List;

/** Read-only access to the orders Outpost stores and what the Ledger booked for them. */
public interface PaymentRepository {
  /** Lists every payment, newest first. */
  List<Payment> findPayments();

  /** Lists the order's lines in line order, each with whether a live refund claims it. */
  List<OrderLine> findOrderLines(String orderReference);

  /** Lists the events booked on the payment and its captures and refunds, oldest first. */
  List<PaymentEvent> findPaymentEvents(String orderReference);

  /** Lists the journal lines posted for the payment and its captures and refunds, by entry. */
  List<PaymentJournalLine> findPaymentJournalLines(String orderReference);
}
