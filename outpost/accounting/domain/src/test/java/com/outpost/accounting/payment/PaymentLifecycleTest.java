package com.outpost.accounting.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.outpost.accounting.AccountingFixtures;
import com.outpost.accounting.Transaction;
import com.outpost.accounting.TransactionEvent;
import com.outpost.accounting.TransactionEventTypes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PaymentLifecycleTest {
  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

  private final PaymentLifecycle lifecycle = new PaymentLifecycle();

  @Test
  void singleOrderCreatedEventIsAcceptedAndIsTheCurrentState() {
    Transaction payment = AccountingFixtures.payment(1L);

    assertEquals(
        TransactionEventTypes.ORDER_CREATED.getValue(),
        lifecycle.construct(List.of(orderCreated(payment, 1L, T0))));
  }

  @Test
  void orderCreatedThenAuthorisedIsAccepted() {
    Transaction payment = AccountingFixtures.payment(2L);

    assertEquals(
        TransactionEventTypes.AUTHORISED.getValue(),
        lifecycle.construct(
            List.of(orderCreated(payment, 1L, T0), authorised(payment, 2L, T0.plusSeconds(1)))));
  }

  @Test
  void authorisedThenCancelledIsAcceptedAndTerminal() {
    Transaction payment = AccountingFixtures.payment(3L);

    assertEquals(
        TransactionEventTypes.CANCELLED.getValue(),
        lifecycle.construct(
            List.of(
                orderCreated(payment, 1L, T0),
                authorised(payment, 2L, T0.plusSeconds(1)),
                event(payment, TransactionEventTypes.CANCELLED, 3L, T0.plusSeconds(2)))));
  }

  @Test
  void anEmptyHistoryIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> lifecycle.construct(List.of()));
  }

  @Test
  void constructsTheSameStateRegardlessOfInputOrder() {
    Transaction payment = AccountingFixtures.payment(4L);
    TransactionEvent orderCreated = orderCreated(payment, 1L, T0);
    TransactionEvent authorised = authorised(payment, 2L, T0.plusSeconds(1));
    TransactionEvent cancelled =
        event(payment, TransactionEventTypes.CANCELLED, 3L, T0.plusSeconds(2));

    assertEquals(
        TransactionEventTypes.CANCELLED.getValue(),
        lifecycle.construct(List.of(cancelled, orderCreated, authorised)));
  }

  @Test
  void equalOccurredAtIsResolvedByTransactionEventId() {
    Transaction payment = AccountingFixtures.payment(5L);
    TransactionEvent orderCreated = orderCreated(payment, 1L, T0);
    TransactionEvent authorised = authorised(payment, 2L, T0);

    assertEquals(
        TransactionEventTypes.AUTHORISED.getValue(),
        lifecycle.construct(List.of(authorised, orderCreated)));
  }

  @Test
  void eventFromAnotherTransactionIsRejected() {
    Transaction payment = AccountingFixtures.payment(6L);
    Transaction otherPayment = AccountingFixtures.payment(7L);

    List<TransactionEvent> events =
        List.of(orderCreated(payment, 1L, T0), authorised(otherPayment, 2L, T0.plusSeconds(1)));

    assertThrows(IllegalArgumentException.class, () -> lifecycle.construct(events));
  }

  @Test
  void nonPaymentTransactionIsRejected() {
    Transaction capture = AccountingFixtures.capture(8L);
    List<TransactionEvent> events = List.of(orderCreated(capture, 1L, T0));

    assertThrows(IllegalArgumentException.class, () -> lifecycle.construct(events));
  }

  @Test
  void canFollowAcceptsTheStartOfHistory() {
    assertTrue(lifecycle.canFollow(null, TransactionEventTypes.ORDER_CREATED.getValue()));
  }

  @Test
  void canFollowRejectsAnEventOtherThanOrderCreatedAsTheStart() {
    assertFalse(lifecycle.canFollow(null, TransactionEventTypes.AUTHORISED.getValue()));
  }

  @Test
  void canFollowAcceptsAuthorisedAfterOrderCreated() {
    assertTrue(
        lifecycle.canFollow(
            TransactionEventTypes.ORDER_CREATED.getValue(),
            TransactionEventTypes.AUTHORISED.getValue()));
  }

  @Test
  void canFollowRejectsAnyEventAfterTerminalState() {
    assertFalse(
        lifecycle.canFollow(
            TransactionEventTypes.REFUSED.getValue(), TransactionEventTypes.AUTHORISED.getValue()));
  }

  @Test
  void foldsPersistedEventTypesInStoredOrder() {
    assertEquals(
        TransactionEventTypes.CANCELLED.getValue(),
        lifecycle.fold(
            List.of(
                TransactionEventTypes.ORDER_CREATED.getValue(),
                TransactionEventTypes.AUTHORISED.getValue(),
                TransactionEventTypes.CANCELLED.getValue())));
  }

  @Test
  void rejectsAnInvalidPersistedEventTypeSequence() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            lifecycle.fold(
                List.of(
                    TransactionEventTypes.ORDER_CREATED.getValue(),
                    TransactionEventTypes.CANCELLED.getValue())));
  }

  @ParameterizedTest
  @MethodSource("rejectedHistories")
  void rejectsHistoryNoValidEventSequenceCouldHaveProduced(List<TransactionEventTypes> eventTypes) {
    Transaction payment = AccountingFixtures.payment(9L);
    List<TransactionEvent> events = toChronologicalEvents(payment, eventTypes);

    assertThrows(IllegalArgumentException.class, () -> lifecycle.construct(events));
  }

  private static Stream<Arguments> rejectedHistories() {
    return Stream.of(
        Arguments.of(List.of(TransactionEventTypes.AUTHORISED)),
        Arguments.of(
            List.of(TransactionEventTypes.ORDER_CREATED, TransactionEventTypes.ORDER_CREATED)),
        Arguments.of(
            List.of(
                TransactionEventTypes.ORDER_CREATED,
                TransactionEventTypes.REFUSED,
                TransactionEventTypes.AUTHORISED)),
        Arguments.of(
            List.of(
                TransactionEventTypes.ORDER_CREATED,
                TransactionEventTypes.AUTHORISED,
                TransactionEventTypes.CANCELLED,
                TransactionEventTypes.AUTHORISED)),
        Arguments.of(List.of(TransactionEventTypes.ORDER_CREATED, TransactionEventTypes.CANCELLED)),
        Arguments.of(
            List.of(
                TransactionEventTypes.ORDER_CREATED,
                TransactionEventTypes.AUTHORISED,
                TransactionEventTypes.AUTHORISED)),
        Arguments.of(
            List.of(
                TransactionEventTypes.ORDER_CREATED,
                TransactionEventTypes.AUTHORISED,
                TransactionEventTypes.CAPTURED)),
        Arguments.of(
            List.of(
                TransactionEventTypes.ORDER_CREATED,
                TransactionEventTypes.AUTHORISED,
                TransactionEventTypes.CAPTURE_FAILED)),
        Arguments.of(
            List.of(
                TransactionEventTypes.ORDER_CREATED,
                TransactionEventTypes.AUTHORISED,
                TransactionEventTypes.REFUND_REQUESTED)),
        Arguments.of(
            List.of(
                TransactionEventTypes.ORDER_CREATED,
                TransactionEventTypes.REFUSED,
                TransactionEventTypes.CANCELLED)));
  }

  private static List<TransactionEvent> toChronologicalEvents(
      Transaction payment, List<TransactionEventTypes> eventTypes) {
    List<TransactionEvent> events = new ArrayList<>();
    long id = 1L;
    Instant occurredAt = T0;
    for (TransactionEventTypes eventType : eventTypes) {
      events.add(event(payment, eventType, id, occurredAt));
      id++;
      occurredAt = occurredAt.plusSeconds(1);
    }
    return events;
  }

  private static TransactionEvent orderCreated(
      Transaction transaction, long transactionEventId, Instant occurredAt) {
    return event(transaction, TransactionEventTypes.ORDER_CREATED, transactionEventId, occurredAt);
  }

  private static TransactionEvent authorised(
      Transaction transaction, long transactionEventId, Instant occurredAt) {
    return event(transaction, TransactionEventTypes.AUTHORISED, transactionEventId, occurredAt);
  }

  private static TransactionEvent event(
      Transaction transaction,
      TransactionEventTypes eventType,
      long transactionEventId,
      Instant occurredAt) {
    return new TransactionEvent(transactionEventId, transaction, eventType.getValue(), occurredAt);
  }
}
