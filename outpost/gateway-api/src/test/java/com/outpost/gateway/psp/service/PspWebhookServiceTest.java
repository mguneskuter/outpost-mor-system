package com.outpost.gateway.psp.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.accounting.api.AccountingQueueRequestTypes;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Currencies;
import com.outpost.framework.queue.QueuedItem;
import com.outpost.framework.queue.TimeOrderedQueue;
import com.outpost.integration.psp.simulator.PspConfiguration;
import com.outpost.payment.ShopperDetail;
import com.outpost.payment.common.Amount;
import com.outpost.payment.common.ProductTypes;
import com.outpost.payment.order.Order;
import com.outpost.payment.order.OrderItem;
import com.outpost.payment.order.repository.OrderRepository;
import com.outpost.payment.refund.Refund;
import com.outpost.payment.refund.RefundItem;
import com.outpost.payment.refund.repository.RefundRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class PspWebhookServiceTest {
  private static final long MERCHANT_ACCOUNT_ID = 10L;
  private static final long PSP_ACCOUNT_ID = 20L;
  private static final long OTHER_PSP_ACCOUNT_ID = 30L;
  private static final String ORDER_REFERENCE = "order-1";
  private static final String MERCHANT_REFERENCE = "merchant-order-1";
  private static final String PSP_REFERENCE = "psp-payment-1";
  private static final String REFUND_REFERENCE = "refund-1";
  private static final PspConfiguration PSP =
      new PspConfiguration(
          PSP_ACCOUNT_ID, "PSP", "https://psp.example.test", "key", "secret", 1, 1);

  private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
  private static final Account ROOT =
      Account.of(1L, AccountTypes.ROOT.getValue(), "ROOT", "Root", true, CREATED, null);
  private static final Account MERCHANT =
      Account.of(
          MERCHANT_ACCOUNT_ID,
          AccountTypes.MERCHANT.getValue(),
          "MERCHANT",
          "Merchant",
          true,
          CREATED,
          ROOT);
  private static final BigDecimal RATE = new BigDecimal("0.1900");
  private static final OrderItem LINE_1 = line(3L, "line-1", 60L, 11L);
  private static final OrderItem LINE_2 = line(4L, "line-2", 40L, 8L);

  private final FakeOrderRepository orders = new FakeOrderRepository();
  private final FakeRefundRepository refunds = new FakeRefundRepository();
  private final TimeOrderedQueue<AccountingQueueRequest> accountingQueue =
      new TimeOrderedQueue<>(Clock.fixed(Instant.parse("2026-09-13T10:00:00Z"), ZoneOffset.UTC), 1);
  private final PspWebhookService service = new PspWebhookService(orders, refunds, accountingQueue);

  @Test
  void refusesSignedEventWhosePspReferenceDiffersFromTheStoredOne() {
    orders.store(ORDER_REFERENCE, PSP_ACCOUNT_ID, PSP_REFERENCE);

    PspWebhookResults result =
        service.queueEvent(PSP, event(PspEventCodes.CAPTURE, "psp-payment-other", null));

    assertThat(result).isEqualTo(PspWebhookResults.PSP_REFERENCE_MISMATCH);
    assertThat(queued()).isEmpty();
  }

  @Test
  void refusesSignedEventForPaymentWithoutStoredPspReference() {
    orders.store(ORDER_REFERENCE, PSP_ACCOUNT_ID, null);

    PspWebhookResults result =
        service.queueEvent(PSP, event(PspEventCodes.AUTHORISATION, PSP_REFERENCE, null));

    assertThat(result).isEqualTo(PspWebhookResults.PSP_REFERENCE_MISMATCH);
    assertThat(queued()).isEmpty();
  }

  @Test
  void refusesEventForPaymentOfAnotherPspAccountDistinctlyFromReferenceMismatch() {
    orders.store(ORDER_REFERENCE, OTHER_PSP_ACCOUNT_ID, PSP_REFERENCE);

    PspWebhookResults result =
        service.queueEvent(PSP, event(PspEventCodes.CAPTURE, PSP_REFERENCE, null));

    assertThat(result).isEqualTo(PspWebhookResults.UNKNOWN_ORDER);
    assertThat(queued()).isEmpty();
  }

  @Test
  void refusesEventForUnknownOrder() {
    PspWebhookResults result =
        service.queueEvent(PSP, event(PspEventCodes.CAPTURE, PSP_REFERENCE, null));

    assertThat(result).isEqualTo(PspWebhookResults.UNKNOWN_ORDER);
    assertThat(queued()).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = PspEventCodes.class,
      names = {"AUTHORISATION", "CAPTURE"})
  void queuesTheRequestForEachAcceptedEvent(PspEventCodes eventCode) {
    orders.store(ORDER_REFERENCE, PSP_ACCOUNT_ID, PSP_REFERENCE);

    PspWebhookResults result = service.queueEvent(PSP, event(eventCode, PSP_REFERENCE, null));

    assertThat(result).isEqualTo(PspWebhookResults.ACCEPTED);
    assertThat(queued())
        .containsExactly(
            new AccountingQueueRequest(
                AccountingQueueRequestTypes.valueOf(eventCode.name()),
                ORDER_REFERENCE,
                MERCHANT_REFERENCE,
                PSP.code(),
                PSP_REFERENCE,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null));
  }

  @Test
  void queuesSuccessfulRefundWithTheStoredRefundsSummedAmounts() {
    orders.store(ORDER_REFERENCE, PSP_ACCOUNT_ID, PSP_REFERENCE);
    refunds.store(storedRefund(false));

    PspWebhookResults result =
        service.queueEvent(PSP, refundEvent(true, 119L, "EUR", echo(LINE_1), echo(LINE_2)));

    assertThat(result).isEqualTo(PspWebhookResults.ACCEPTED);
    assertThat(queued())
        .containsExactly(
            new AccountingQueueRequest(
                AccountingQueueRequestTypes.REFUND,
                ORDER_REFERENCE,
                MERCHANT_REFERENCE,
                PSP.code(),
                PSP_REFERENCE,
                true,
                REFUND_REFERENCE,
                null,
                null,
                null,
                eur(100L),
                eur(19L),
                eur(119L)));
    assertThat(refunds.pspRefundReferences).containsEntry(REFUND_REFERENCE, "psp-refund-1");
    assertThat(refunds.failed).isEmpty();
  }

  @Test
  void marksTheLinesFailedAndQueuesTheFailedRefund() {
    orders.store(ORDER_REFERENCE, PSP_ACCOUNT_ID, PSP_REFERENCE);
    refunds.store(storedRefund(false));

    PspWebhookResults result =
        service.queueEvent(PSP, refundEvent(false, 119L, "EUR", echo(LINE_1), echo(LINE_2)));

    assertThat(result).isEqualTo(PspWebhookResults.ACCEPTED);
    assertThat(refunds.failed).containsExactly(REFUND_REFERENCE);
    assertThat(queued())
        .singleElement()
        .satisfies(
            request -> {
              assertThat(request.type()).isEqualTo(AccountingQueueRequestTypes.REFUND);
              assertThat(request.success()).isFalse();
              assertThat(request.refundReference()).isEqualTo(REFUND_REFERENCE);
              assertThat(request.netAmount()).isEqualTo(eur(100L));
              assertThat(request.taxAmount()).isEqualTo(eur(19L));
              assertThat(request.grossAmount()).isEqualTo(eur(119L));
            });
  }

  @Test
  void refusesRefundEventNamingNoStoredRefundOfTheOrder() {
    orders.store(ORDER_REFERENCE, PSP_ACCOUNT_ID, PSP_REFERENCE);

    PspWebhookResults result =
        service.queueEvent(PSP, refundEvent(true, 119L, "EUR", echo(LINE_1), echo(LINE_2)));

    assertThat(result).isEqualTo(PspWebhookResults.UNKNOWN_REFUND);
    assertThat(queued()).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("eventsNotEchoingTheStoredRefund")
  void refusesRefundEventThatDoesNotEchoTheStoredRefund(String differs, PspOrderEvent event) {
    orders.store(ORDER_REFERENCE, PSP_ACCOUNT_ID, PSP_REFERENCE);
    refunds.store(storedRefund(false));

    PspWebhookResults result = service.queueEvent(PSP, event);

    assertThat(result).isEqualTo(PspWebhookResults.INVALID_PAYLOAD);
    assertThat(queued()).isEmpty();
    assertThat(refunds.failed).isEmpty();
  }

  static Stream<Arguments> eventsNotEchoingTheStoredRefund() {
    return Stream.of(
        Arguments.of("amount", refundEvent(true, 118L, "EUR", echo(LINE_1), echo(LINE_2))),
        Arguments.of("currency", refundEvent(true, 119L, "USD", echo(LINE_1), echo(LINE_2))),
        Arguments.of(
            "a line's net",
            refundEvent(
                true, 119L, "EUR", new PspRefundLine("line-1", RATE, 59L, 71L), echo(LINE_2))),
        Arguments.of(
            "a line's rate",
            refundEvent(
                true,
                119L,
                "EUR",
                new PspRefundLine("line-1", new BigDecimal("0.21"), 60L, 71L),
                echo(LINE_2))),
        Arguments.of("the set of lines", refundEvent(true, 119L, "EUR", echo(LINE_1))),
        Arguments.of("no lines", refundEvent(true, 119L, "EUR")));
  }

  @Test
  void refusesSuccessfulRefundEventForRefundThatFailed() {
    orders.store(ORDER_REFERENCE, PSP_ACCOUNT_ID, PSP_REFERENCE);
    refunds.store(storedRefund(true));

    PspWebhookResults result =
        service.queueEvent(PSP, refundEvent(true, 119L, "EUR", echo(LINE_1), echo(LINE_2)));

    assertThat(result).isEqualTo(PspWebhookResults.INVALID_PAYLOAD);
    assertThat(queued()).isEmpty();
  }

  @Test
  void refusesRefundEventNamingAnotherPspRefundReferenceThanTheStoredOne() {
    orders.store(ORDER_REFERENCE, PSP_ACCOUNT_ID, PSP_REFERENCE);
    refunds.store(storedRefund(false, "psp-refund-0"));

    PspWebhookResults result =
        service.queueEvent(PSP, refundEvent(true, 119L, "EUR", echo(LINE_1), echo(LINE_2)));

    assertThat(result).isEqualTo(PspWebhookResults.INVALID_PAYLOAD);
    assertThat(queued()).isEmpty();
    assertThat(refunds.pspRefundReferences).isEmpty();
  }

  @Test
  void refusesRefundEventWithoutRefundReference() {
    orders.store(ORDER_REFERENCE, PSP_ACCOUNT_ID, PSP_REFERENCE);

    PspWebhookResults result =
        service.queueEvent(PSP, event(PspEventCodes.REFUND, PSP_REFERENCE, " "));

    assertThat(result).isEqualTo(PspWebhookResults.INVALID_PAYLOAD);
    assertThat(queued()).isEmpty();
  }

  @Test
  void refusesMatchingEventWhenTheAccountingQueueIsFull() {
    orders.store(ORDER_REFERENCE, PSP_ACCOUNT_ID, PSP_REFERENCE);
    service.queueEvent(PSP, event(PspEventCodes.AUTHORISATION, PSP_REFERENCE, null));

    PspWebhookResults result =
        service.queueEvent(PSP, event(PspEventCodes.CAPTURE, PSP_REFERENCE, null));

    assertThat(result).isEqualTo(PspWebhookResults.QUEUE_FULL);
    assertThat(queued())
        .extracting(AccountingQueueRequest::type)
        .containsExactly(AccountingQueueRequestTypes.AUTHORISATION);
  }

  private List<AccountingQueueRequest> queued() {
    List<AccountingQueueRequest> requests = new ArrayList<>();
    Optional<QueuedItem<AccountingQueueRequest>> item;
    while ((item = accountingQueue.poll()).isPresent()) {
      requests.add(item.orElseThrow().payload());
    }
    return requests;
  }

  private static PspOrderEvent event(
      PspEventCodes eventCode, String pspReference, @Nullable String refundReference) {
    return new PspOrderEvent(
        PSP.code(),
        pspReference,
        ORDER_REFERENCE,
        eventCode,
        true,
        "APPROVED",
        refundReference,
        null,
        119L,
        "EUR",
        List.of());
  }

  private static PspOrderEvent refundEvent(
      boolean success, long amount, String currency, PspRefundLine... lines) {
    return new PspOrderEvent(
        PSP.code(),
        PSP_REFERENCE,
        ORDER_REFERENCE,
        PspEventCodes.REFUND,
        success,
        success ? "APPROVED" : "ACQUIRER_REFUSED",
        REFUND_REFERENCE,
        "psp-refund-1",
        amount,
        currency,
        List.of(lines));
  }

  /** The line as the PSP echoes it, with a rate of fewer decimals than the stored one. */
  private static PspRefundLine echo(OrderItem line) {
    return new PspRefundLine(
        line.getOrderLineReference(),
        new BigDecimal("0.19"),
        line.getNetAmount().quantity(),
        line.getNetAmount().plus(line.getTaxAmount()).quantity());
  }

  private static Refund storedRefund(boolean failed) {
    return storedRefund(failed, null);
  }

  private static Refund storedRefund(boolean failed, @Nullable String pspRefundReference) {
    return new Refund(
        7L,
        REFUND_REFERENCE,
        1L,
        ORDER_REFERENCE,
        "merchant-refund-1",
        "refund-key-1",
        pspRefundReference,
        List.of(new RefundItem(8L, LINE_1, failed), new RefundItem(9L, LINE_2, failed)),
        CREATED);
  }

  private static OrderItem line(long orderItemId, String reference, long net, long tax) {
    return new OrderItem(
        orderItemId,
        ProductTypes.DIGITAL_GOODS.getValue(),
        reference,
        "merchant-" + reference,
        eur(net),
        eur(tax),
        RATE);
  }

  private static Amount eur(long quantity) {
    return new Amount(Currencies.EUR.getValue(), quantity);
  }

  private static final class FakeOrderRepository implements OrderRepository {
    private final Map<String, Order> orders = new HashMap<>();

    void store(String orderReference, long pspAccountId, @Nullable String pspReference) {
      orders.put(
          orderReference,
          new Order(
              1L,
              orderReference,
              MERCHANT_REFERENCE,
              MERCHANT,
              2L,
              Countries.GERMANY.getValue(),
              null,
              eur(100L),
              eur(19L),
              eur(119L),
              orderReference + "-key",
              orderReference + "-fingerprint",
              Account.of(
                  pspAccountId,
                  AccountTypes.PSP.getValue(),
                  "PSP-" + pspAccountId,
                  "PSP",
                  true,
                  CREATED,
                  ROOT),
              pspReference,
              pspReference == null ? null : "https://pay.example/" + orderReference,
              Instant.parse("2026-09-12T00:00:00Z"),
              List.of(LINE_1, LINE_2)));
    }

    @Override
    public Optional<Order> findOrderByIdempotencyKey(long accountId, String idempotencyKey) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Order> findOrderByOrderReference(String orderReference) {
      return Optional.ofNullable(orders.get(orderReference));
    }

    @Override
    public Optional<Order> insertOrder(ShopperDetail shopper, Order order) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void updateOrderPspReferenceAndPaymentLink(
        String orderReference, String pspReference, String paymentLink) {
      throw new UnsupportedOperationException();
    }
  }

  /** Stored refunds by reference, and what the service asked to store on them. */
  private static final class FakeRefundRepository implements RefundRepository {
    private final Map<String, Refund> refunds = new HashMap<>();
    private final Map<String, String> pspRefundReferences = new HashMap<>();
    private final List<String> failed = new ArrayList<>();

    void store(Refund refund) {
      refunds.put(refund.refundReference(), refund);
    }

    @Override
    public Optional<Refund> insertRefund(Refund refund) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Refund> findRefundByRefundReference(String refundReference) {
      return Optional.ofNullable(refunds.get(refundReference));
    }

    @Override
    public List<Refund> findRefundsByOriginalReference(String originalReference) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void updateRefundPspRefundReference(String refundReference, String pspRefundReference) {
      pspRefundReferences.put(refundReference, pspRefundReference);
    }

    @Override
    public void updateRefundItemRefundFailed(String refundReference) {
      failed.add(refundReference);
    }
  }
}
