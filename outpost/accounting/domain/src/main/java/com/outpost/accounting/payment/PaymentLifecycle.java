package com.outpost.accounting.payment;

import com.outpost.accounting.Transaction;
import com.outpost.accounting.TransactionEvent;
import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionEventTypes.TransactionEventType;
import com.outpost.accounting.TransactionTypes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The PAYMENT transaction's state machine over its own recorded events.
 *
 * <p>The only valid edges are {@code no event -> ORDER_CREATED}, {@code ORDER_CREATED ->
 * AUTHORISED}, {@code ORDER_CREATED -> REFUSED}, and {@code AUTHORISED -> CANCELLED}. {@code
 * REFUSED} and {@code CANCELLED} are terminal.
 */
public final class PaymentLifecycle {
  private static final Comparator<TransactionEvent> CHRONOLOGICAL_ORDER =
      Comparator.comparing(TransactionEvent::getOccurredAt)
          .thenComparingLong(TransactionEvent::getTransactionEventId);

  /**
   * Returns the state after constructing {@code events} chronologically.
   *
   * @throws IllegalArgumentException if {@code events} is empty, any event does not belong to the
   *     same PAYMENT transaction as the others, or an event cannot follow the state reached by the
   *     events before it
   */
  public TransactionEventType construct(List<TransactionEvent> events) {
    Objects.requireNonNull(events, "events");
    if (events.isEmpty()) {
      throw new IllegalArgumentException("a payment must have at least one event");
    }
    Transaction paymentTransaction = events.get(0).getTransaction();
    if (!paymentTransaction.getTransactionType().equals(TransactionTypes.PAYMENT.getValue())) {
      throw new IllegalArgumentException(
          "events must belong to a PAYMENT transaction: "
              + paymentTransaction.getTransactionType().getCode());
    }
    List<TransactionEvent> chronological = new ArrayList<>(events);
    chronological.sort(CHRONOLOGICAL_ORDER);

    Iterator<TransactionEvent> chronologicalEvents = chronological.iterator();
    TransactionEvent first = chronologicalEvents.next();
    if (!first.getTransaction().equals(paymentTransaction)) {
      throw new IllegalArgumentException("events must all belong to the same transaction");
    }
    TransactionEventType state = transition(null, first.getTransactionEventType());
    while (chronologicalEvents.hasNext()) {
      TransactionEvent event = chronologicalEvents.next();
      if (!event.getTransaction().equals(paymentTransaction)) {
        throw new IllegalArgumentException("events must all belong to the same transaction");
      }
      state = transition(state, event.getTransactionEventType());
    }
    return state;
  }

  /**
   * Returns whether {@code candidate} may follow {@code state}.
   *
   * @param state the payment's current state, or {@code null} when it has no event yet
   */
  public boolean canFollow(@Nullable TransactionEventType state, TransactionEventType candidate) {
    Objects.requireNonNull(candidate, "candidate");
    return allowedNextEvents(state).contains(candidate);
  }

  private static TransactionEventType transition(
      @Nullable TransactionEventType current, TransactionEventType next) {
    List<TransactionEventType> allowed = allowedNextEvents(current);
    if (!allowed.contains(next)) {
      throw new IllegalArgumentException(
          "event "
              + next.getCode()
              + " cannot follow "
              + (current == null ? "no event" : current.getCode()));
    }
    return next;
  }

  private static List<TransactionEventType> allowedNextEvents(
      @Nullable TransactionEventType current) {
    if (current == null) {
      return List.of(TransactionEventTypes.ORDER_CREATED.getValue());
    }
    if (current.equals(TransactionEventTypes.ORDER_CREATED.getValue())) {
      return List.of(
          TransactionEventTypes.AUTHORISED.getValue(), TransactionEventTypes.REFUSED.getValue());
    }
    if (current.equals(TransactionEventTypes.AUTHORISED.getValue())) {
      return List.of(TransactionEventTypes.CANCELLED.getValue());
    }
    return List.of();
  }
}
