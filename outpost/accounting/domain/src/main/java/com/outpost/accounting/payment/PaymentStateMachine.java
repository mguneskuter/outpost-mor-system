package com.outpost.accounting.payment;

import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionEventTypes.TransactionEventType;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The PAYMENT transaction's state machine over its own recorded events.
 *
 * <p>The PAYMENT root edges are {@code no event -> ORDER_CREATED}, {@code ORDER_CREATED ->
 * AUTHORISED}, {@code ORDER_CREATED -> REFUSED}, and {@code AUTHORISED -> CANCELLED}. CAPTURE and
 * REFUND transactions follow their own transition rules. {@code REFUSED}, {@code CANCELLED}, and
 * the terminal events of those transactions cannot be followed.
 */
public final class PaymentStateMachine {
  /**
   * Folds persisted payment event types in their stored order.
   *
   * @throws IllegalArgumentException if {@code eventTypes} is empty or contains an invalid
   *     transition
   */
  public TransactionEventType fold(List<TransactionEventType> eventTypes) {
    Objects.requireNonNull(eventTypes, "eventTypes");
    if (eventTypes.isEmpty()) {
      throw new IllegalArgumentException("A payment must have at least one event");
    }
    TransactionEventType state = null;
    for (TransactionEventType eventType : eventTypes) {
      state = transition(state, Objects.requireNonNull(eventType, "eventType"));
    }
    return Objects.requireNonNull(state);
  }

  /**
   * Folds payment event types, then the event type of the payment's CAPTURE transaction when there
   * is one.
   *
   * @throws IllegalArgumentException if either contains an invalid transition
   */
  public TransactionEventType fold(
      List<TransactionEventType> paymentEventTypes,
      @Nullable TransactionEventType captureEventType) {
    TransactionEventType paymentState = fold(paymentEventTypes);
    if (captureEventType == null) {
      return paymentState;
    }
    if (!isNextCapture(paymentState, false, captureEventType)) {
      throw new IllegalArgumentException(
          "Capture event cannot follow payment state: " + paymentState.getCode());
    }
    return captureEventType;
  }

  /**
   * Returns whether {@code candidate} may follow {@code state}.
   *
   * @param state the payment's current state, or {@code null} when it has no event yet
   */
  public boolean isNext(@Nullable TransactionEventType state, TransactionEventType candidate) {
    Objects.requireNonNull(candidate, "candidate");
    return allowedNextEvents(state).contains(candidate);
  }

  /** Returns whether a root event may follow when a capture child exists. */
  public boolean isNext(
      @Nullable TransactionEventType state,
      TransactionEventType candidate,
      boolean captureChildExists) {
    Objects.requireNonNull(candidate, "candidate");
    if (captureChildExists && candidate.equals(TransactionEventTypes.CANCELLED.getValue())) {
      return false;
    }
    return isNext(state, candidate);
  }

  /**
   * Returns whether the payment's CAPTURE transaction may record {@code candidate}, its one event.
   */
  public boolean isNextCapture(
      @Nullable TransactionEventType paymentState,
      boolean captureChildExists,
      TransactionEventType candidate) {
    Objects.requireNonNull(candidate, "candidate");
    return !captureChildExists
        && paymentState != null
        && paymentState.equals(TransactionEventTypes.AUTHORISED.getValue())
        && (candidate.equals(TransactionEventTypes.CAPTURED.getValue())
            || candidate.equals(TransactionEventTypes.CAPTURE_FAILED.getValue()));
  }

  private static TransactionEventType transition(
      @Nullable TransactionEventType current, TransactionEventType next) {
    List<TransactionEventType> allowed = allowedNextEvents(current);
    if (!allowed.contains(next)) {
      throw new IllegalArgumentException(
          "Event "
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
