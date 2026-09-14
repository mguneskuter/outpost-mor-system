package com.outpost.accounting.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.TransactionEventTypes.TransactionEventType;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PaymentStateMachineTest {
  private final PaymentStateMachine stateMachine = new PaymentStateMachine();

  @Test
  void isNextAcceptsTheStartOfHistory() {
    assertTrue(stateMachine.isNext(null, TransactionEventTypes.ORDER_CREATED.getValue()));
  }

  @Test
  void isNextRejectsAnEventOtherThanOrderCreatedAsTheStart() {
    assertFalse(stateMachine.isNext(null, TransactionEventTypes.AUTHORISED.getValue()));
  }

  @Test
  void isNextAcceptsAuthorisedAfterOrderCreated() {
    assertTrue(
        stateMachine.isNext(
            TransactionEventTypes.ORDER_CREATED.getValue(),
            TransactionEventTypes.AUTHORISED.getValue()));
  }

  @Test
  void isNextRejectsAnyEventAfterTerminalState() {
    assertFalse(
        stateMachine.isNext(
            TransactionEventTypes.REFUSED.getValue(), TransactionEventTypes.AUTHORISED.getValue()));
  }

  @Test
  void captureResultMayFollowAnAuthorisedPaymentOnlyWhenThereIsNoCaptureChild() {
    assertTrue(
        stateMachine.isNextCapture(
            TransactionEventTypes.AUTHORISED.getValue(),
            false,
            TransactionEventTypes.CAPTURED.getValue()));
    assertTrue(
        stateMachine.isNextCapture(
            TransactionEventTypes.AUTHORISED.getValue(),
            false,
            TransactionEventTypes.CAPTURE_FAILED.getValue()));
    assertFalse(
        stateMachine.isNextCapture(
            TransactionEventTypes.ORDER_CREATED.getValue(),
            false,
            TransactionEventTypes.CAPTURED.getValue()));
    assertFalse(
        stateMachine.isNextCapture(
            TransactionEventTypes.AUTHORISED.getValue(),
            true,
            TransactionEventTypes.CAPTURED.getValue()));
  }

  @Test
  void cancellationIsRejectedAfterCaptureChildExists() {
    assertFalse(
        stateMachine.isNext(
            TransactionEventTypes.AUTHORISED.getValue(),
            TransactionEventTypes.CANCELLED.getValue(),
            true));
    assertTrue(
        stateMachine.isNext(
            TransactionEventTypes.AUTHORISED.getValue(),
            TransactionEventTypes.CANCELLED.getValue(),
            false));
  }

  @Test
  void foldAcceptsCaptureEventOnlyAfterAuthorisation() {
    assertEquals(
        TransactionEventTypes.CAPTURED.getValue(),
        stateMachine.fold(
            List.of(
                TransactionEventTypes.ORDER_CREATED.getValue(),
                TransactionEventTypes.AUTHORISED.getValue()),
            TransactionEventTypes.CAPTURED.getValue()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            stateMachine.fold(
                List.of(TransactionEventTypes.ORDER_CREATED.getValue()),
                TransactionEventTypes.CAPTURE_FAILED.getValue()));
  }

  @Test
  void foldsPersistedEventTypesInStoredOrder() {
    assertEquals(
        TransactionEventTypes.CANCELLED.getValue(),
        stateMachine.fold(
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
            stateMachine.fold(
                List.of(
                    TransactionEventTypes.ORDER_CREATED.getValue(),
                    TransactionEventTypes.CANCELLED.getValue())));
  }

  @ParameterizedTest
  @MethodSource("rejectedHistories")
  void rejectsHistoryNoValidEventSequenceCouldHaveProduced(List<TransactionEventTypes> eventTypes) {
    List<TransactionEventType> history =
        eventTypes.stream().map(TransactionEventTypes::getValue).toList();

    assertThrows(IllegalArgumentException.class, () -> stateMachine.fold(history));
  }

  @Test
  void anEmptyHistoryIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> stateMachine.fold(List.of()));
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
}
