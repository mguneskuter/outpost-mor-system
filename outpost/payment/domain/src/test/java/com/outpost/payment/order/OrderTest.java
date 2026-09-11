package com.outpost.payment.order;

import static com.outpost.payment.order.OrderFixtures.eur;
import static com.outpost.payment.order.OrderFixtures.item;
import static com.outpost.payment.order.OrderFixtures.order;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderTest {
  @Test
  void acceptsAnOrderWhoseTotalsEqualTheSumOfItsLines() {
    OrderItem line1 = item(1L, "OLR-1", "MLR-1");
    OrderItem line2 = item(2L, "OLR-2", "MLR-2");
    Order order = order(1L, List.of(line1, line2), eur(2000L), eur(420L));

    assertEquals(eur(2000L), order.getNetAmount());
    assertEquals(eur(420L), order.getTaxAmount());
    assertEquals(eur(2420L), order.getGrossAmount());
    assertEquals(2, order.getItems().size());
  }

  @Test
  void rejectsAnOrderWhoseNetAmountDoesNotEqualTheSumOfItsLines() {
    OrderItem line = item(1L, "OLR-1", "MLR-1");

    assertThrows(
        IllegalArgumentException.class, () -> order(1L, List.of(line), eur(999L), eur(210L)));
  }

  @Test
  void rejectsDuplicateMerchantLineReferencesWithinTheOrder() {
    OrderItem line1 = item(1L, "OLR-1", "MLR-DUP");
    OrderItem line2 = item(2L, "OLR-2", "MLR-DUP");

    assertThrows(
        IllegalArgumentException.class,
        () -> order(1L, List.of(line1, line2), eur(2000L), eur(420L)));
  }

  @Test
  void rejectsDuplicateOrderLineReferencesWithinTheOrder() {
    OrderItem line1 = item(1L, "OLR-DUP", "MLR-1");
    OrderItem line2 = item(2L, "OLR-DUP", "MLR-2");

    assertThrows(
        IllegalArgumentException.class,
        () -> order(1L, List.of(line1, line2), eur(2000L), eur(420L)));
  }

  @Test
  void rejectsLineWithDifferentCurrencyThanOrder() {
    OrderItem euroLine = item(1L, "OLR-1", "MLR-1");
    Amount usdNet = new Amount(Currencies.USD.getValue(), 1000L);
    Amount usdTax = new Amount(Currencies.USD.getValue(), 210L);

    assertThrows(
        IllegalArgumentException.class, () -> order(1L, List.of(euroLine), usdNet, usdTax));
  }

  @Test
  void rejectsNegativeOrderAmount() {
    OrderItem line = item(1L, "OLR-1", "MLR-1");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Order(
                1L,
                "order-ref",
                "merchant-ref",
                100L,
                200L,
                new Amount(Currencies.EUR.getValue(), -1L),
                eur(0L),
                eur(-1L),
                "idempotency",
                OrderFixtures.CREATED,
                List.of(line)));
  }

  @Test
  void rejectsAnOrderWithNoLines() {
    assertThrows(IllegalArgumentException.class, () -> order(1L, List.of(), eur(0L), eur(0L)));
  }
}
