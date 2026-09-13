package com.outpost.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.accounting.api.AccountingQueueRequest;
import com.outpost.accounting.api.AccountingQueueRequestTypes;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Currencies;
import com.outpost.framework.queue.QueuedItem;
import com.outpost.framework.queue.TimeOrderedQueue;
import com.outpost.gateway.psp.service.PspOrderEvent;
import com.outpost.gateway.psp.service.PspWebhookEventCodes;
import com.outpost.gateway.psp.service.PspWebhookProcessResultCodes;
import com.outpost.gateway.psp.service.PspWebhookService;
import com.outpost.integration.psp.simulator.repository.PspConfiguration;
import com.outpost.payment.ShopperDetail;
import com.outpost.payment.common.Amount;
import com.outpost.payment.common.ProductTypes;
import com.outpost.payment.order.Order;
import com.outpost.payment.order.OrderItem;
import com.outpost.payment.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PspWebhookServiceTest {
  private static final long MERCHANT_ACCOUNT_ID = 10L;
  private static final long PSP_ACCOUNT_ID = 20L;
  private static final long OTHER_PSP_ACCOUNT_ID = 30L;
  private static final String ORDER_REFERENCE = "order-1";
  private static final String MERCHANT_REFERENCE = "merchant-order-1";
  private static final String PSP_REFERENCE = "psp-payment-1";
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

  private final FakeOrderRepository orders = new FakeOrderRepository();
  private final TimeOrderedQueue<AccountingQueueRequest> accountingQueue =
      new TimeOrderedQueue<>(Clock.fixed(Instant.parse("2026-09-13T10:00:00Z"), ZoneOffset.UTC), 1);
  private final PspWebhookService service = new PspWebhookService(orders, accountingQueue);

  @Test
  void refusesSignedEventWhosePspReferenceDiffersFromTheStoredOne() {
    orders.store(ORDER_REFERENCE, PSP_ACCOUNT_ID, PSP_REFERENCE);

    PspWebhookProcessResultCodes result =
        service.process(PSP, event(PspWebhookEventCodes.CAPTURE, "psp-payment-other", null));

    assertThat(result).isEqualTo(PspWebhookProcessResultCodes.PSP_REFERENCE_MISMATCH);
    assertThat(queued()).isEmpty();
  }

  @Test
  void refusesSignedEventForPaymentWithoutStoredPspReference() {
    orders.store(ORDER_REFERENCE, PSP_ACCOUNT_ID, null);

    PspWebhookProcessResultCodes result =
        service.process(PSP, event(PspWebhookEventCodes.AUTHORISATION, PSP_REFERENCE, null));

    assertThat(result).isEqualTo(PspWebhookProcessResultCodes.PSP_REFERENCE_MISMATCH);
    assertThat(queued()).isEmpty();
  }

  @Test
  void refusesEventForPaymentOfAnotherPspAccountDistinctlyFromReferenceMismatch() {
    orders.store(ORDER_REFERENCE, OTHER_PSP_ACCOUNT_ID, PSP_REFERENCE);

    PspWebhookProcessResultCodes result =
        service.process(PSP, event(PspWebhookEventCodes.CAPTURE, PSP_REFERENCE, null));

    assertThat(result).isEqualTo(PspWebhookProcessResultCodes.FOREIGN_PAYMENT);
    assertThat(queued()).isEmpty();
  }

  @Test
  void refusesEventForUnknownPayment() {
    PspWebhookProcessResultCodes result =
        service.process(PSP, event(PspWebhookEventCodes.CAPTURE, PSP_REFERENCE, null));

    assertThat(result).isEqualTo(PspWebhookProcessResultCodes.UNKNOWN_PAYMENT);
    assertThat(queued()).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = PspWebhookEventCodes.class,
      names = {"AUTHORISATION", "CAPTURE"})
  void queuesTheRequestForEachAcceptedEvent(PspWebhookEventCodes eventCode) {
    orders.store(ORDER_REFERENCE, PSP_ACCOUNT_ID, PSP_REFERENCE);

    PspWebhookProcessResultCodes result =
        service.process(PSP, event(eventCode, PSP_REFERENCE, null));

    assertThat(result).isEqualTo(PspWebhookProcessResultCodes.ACCEPTED);
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
  void queuesRefundWithItsRefundReference() {
    orders.store(ORDER_REFERENCE, PSP_ACCOUNT_ID, PSP_REFERENCE);

    PspWebhookProcessResultCodes result =
        service.process(PSP, event(PspWebhookEventCodes.REFUND, PSP_REFERENCE, "refund-1"));

    assertThat(result).isEqualTo(PspWebhookProcessResultCodes.ACCEPTED);
    assertThat(queued())
        .singleElement()
        .satisfies(
            request -> {
              assertThat(request.type()).isEqualTo(AccountingQueueRequestTypes.REFUND);
              assertThat(request.originalReference()).isEqualTo(ORDER_REFERENCE);
              assertThat(request.refundReference()).isEqualTo("refund-1");
              assertThat(request.success()).isTrue();
            });
  }

  @Test
  void refusesRefundEventWithoutRefundReference() {
    orders.store(ORDER_REFERENCE, PSP_ACCOUNT_ID, PSP_REFERENCE);

    PspWebhookProcessResultCodes result =
        service.process(PSP, event(PspWebhookEventCodes.REFUND, PSP_REFERENCE, " "));

    assertThat(result).isEqualTo(PspWebhookProcessResultCodes.INVALID_PAYLOAD);
    assertThat(queued()).isEmpty();
  }

  @Test
  void refusesMatchingEventWhenTheAccountingQueueIsFull() {
    orders.store(ORDER_REFERENCE, PSP_ACCOUNT_ID, PSP_REFERENCE);
    service.process(PSP, event(PspWebhookEventCodes.AUTHORISATION, PSP_REFERENCE, null));

    PspWebhookProcessResultCodes result =
        service.process(PSP, event(PspWebhookEventCodes.CAPTURE, PSP_REFERENCE, null));

    assertThat(result).isEqualTo(PspWebhookProcessResultCodes.QUEUE_FULL);
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
      PspWebhookEventCodes eventCode, String pspReference, @Nullable String refundReference) {
    return new PspOrderEvent(
        PSP.code(), pspReference, ORDER_REFERENCE, eventCode, true, refundReference);
  }

  private static final class FakeOrderRepository implements OrderRepository {
    private final Map<String, Order> orders = new HashMap<>();

    void store(String orderReference, long pspAccountId, @Nullable String pspReference) {
      Amount net = new Amount(Currencies.EUR.getValue(), 100L);
      Amount tax = new Amount(Currencies.EUR.getValue(), 19L);
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
              net,
              tax,
              net.plus(tax),
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
              List.of(
                  new OrderItem(
                      3L,
                      ProductTypes.DIGITAL_GOODS.getValue(),
                      "line-1",
                      "merchant-line-1",
                      net,
                      tax,
                      new BigDecimal("0.19")))));
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
}
