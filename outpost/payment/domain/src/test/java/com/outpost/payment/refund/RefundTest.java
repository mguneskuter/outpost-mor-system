package com.outpost.payment.refund;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.payment.common.Amount;
import com.outpost.payment.common.ProductTypes;
import com.outpost.payment.order.OrderItem;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class RefundTest {
  private static final Currency EUR = Currencies.EUR.getValue();
  private static final Currency USD = Currencies.USD.getValue();

  @Test
  void rejectsBlankRefundReference() {
    assertThrows(
        IllegalArgumentException.class, () -> refund(" ", 1L, List.of(item(1L, EUR, 100L, 19L))));
  }

  @Test
  void rejectsNonPositiveOrderId() {
    assertThrows(
        IllegalArgumentException.class,
        () -> refund("refund-1", 0L, List.of(item(1L, EUR, 100L, 19L))));
  }

  @Test
  void rejectsRefundWithoutLines() {
    assertThrows(IllegalArgumentException.class, () -> refund("refund-1", 1L, List.of()));
  }

  @Test
  void rejectsLinesOfDifferentCurrencies() {
    assertThrows(
        IllegalArgumentException.class,
        () -> refund("refund-1", 1L, List.of(item(1L, EUR, 100L, 19L), item(2L, USD, 100L, 19L))));
  }

  @Test
  void rejectsLineThatIsNotStored() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new RefundItem(null, orderItem(null, EUR, 100L, 19L), false));
  }

  @Test
  void sumsTheLinesNetTaxAndGross() {
    Refund refund =
        refund("refund-1", 1L, List.of(item(1L, EUR, 100L, 19L), item(2L, EUR, 50L, 10L)));

    assertEquals(new Amount(EUR, 150L), refund.netAmount());
    assertEquals(new Amount(EUR, 29L), refund.taxAmount());
    assertEquals(new Amount(EUR, 179L), refund.grossAmount());
  }

  private static Refund refund(String refundReference, long orderId, List<RefundItem> items) {
    return new Refund(
        null, refundReference, orderId, "order-1", "merchant-refund-1", "key-1", null, items, null);
  }

  private static RefundItem item(long orderItemId, Currency currency, long net, long tax) {
    return new RefundItem(null, orderItem(orderItemId, currency, net, tax), false);
  }

  private static OrderItem orderItem(
      @Nullable Long orderItemId, Currency currency, long net, long tax) {
    return new OrderItem(
        orderItemId,
        ProductTypes.DIGITAL_GOODS.getValue(),
        "line-" + orderItemId,
        "merchant-line-" + orderItemId,
        new Amount(currency, net),
        new Amount(currency, tax),
        new BigDecimal("0.19"));
  }
}
