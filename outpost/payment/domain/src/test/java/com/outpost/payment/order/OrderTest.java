package com.outpost.payment.order;

import static com.outpost.payment.order.OrderFixtures.eur;
import static com.outpost.payment.order.OrderFixtures.item;
import static com.outpost.payment.order.OrderFixtures.order;
import static com.outpost.payment.order.OrderFixtures.unsavedItem;
import static com.outpost.payment.order.OrderFixtures.unsavedOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.outpost.common.iso.Countries;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderTest {
  @Test
  void acceptsAnOrderWhoseTotalsEqualTheSumOfItsLines() {
    OrderItem line1 = item(1L, "OLR-1", "MLR-1");
    OrderItem line2 = item(2L, "OLR-2", "MLR-2");
    Order order = order(List.of(line1, line2), eur(2000L), eur(420L));

    assertEquals(eur(2000L), order.getNetAmount());
    assertEquals(eur(420L), order.getTaxAmount());
    assertEquals(eur(2420L), order.getGrossAmount());
    assertEquals(2, order.getItems().size());
  }

  @Test
  void rejectsAnOrderWhoseNetAmountDoesNotEqualTheSumOfItsLines() {
    OrderItem line = item(1L, "OLR-1", "MLR-1");

    assertThrows(IllegalArgumentException.class, () -> order(List.of(line), eur(999L), eur(210L)));
  }

  @Test
  void rejectsAnOrderWhoseTaxAmountDoesNotEqualTheSumOfItsLines() {
    OrderItem line = item(1L, "OLR-1", "MLR-1");

    assertThrows(IllegalArgumentException.class, () -> order(List.of(line), eur(1000L), eur(211L)));
  }

  @Test
  void rejectsAnUnsavedOrderWhoseTotalsDoNotEqualTheSumOfItsLines() {
    OrderItem line = unsavedItem("OLR-1", "MLR-1");

    assertThrows(
        IllegalArgumentException.class, () -> unsavedOrder(List.of(line), eur(1000L), eur(211L)));
  }

  @Test
  void anUnsavedOrderHasNoIds() {
    Order order = unsavedOrder(List.of(unsavedItem("OLR-1", "MLR-1")), eur(1000L), eur(210L));

    assertTrue(order.getOrderId().isEmpty());
    assertTrue(order.getShopperId().isEmpty());
    assertTrue(order.getItems().get(0).getOrderItemId().isEmpty());
  }

  @Test
  void storedOrderCarriesItsIds() {
    Order order = order(List.of(item(7L, "OLR-1", "MLR-1")), eur(1000L), eur(210L));

    assertEquals(1L, order.getOrderId().getAsLong());
    assertEquals(200L, order.getShopperId().getAsLong());
    assertEquals(7L, order.getItems().get(0).getOrderItemId().getAsLong());
  }

  @Test
  void rejectsDuplicateMerchantLineReferencesWithinTheOrder() {
    OrderItem line1 = item(1L, "OLR-1", "MLR-DUP");
    OrderItem line2 = item(2L, "OLR-2", "MLR-DUP");

    assertThrows(
        IllegalArgumentException.class, () -> order(List.of(line1, line2), eur(2000L), eur(420L)));
  }

  @Test
  void rejectsDuplicateOrderLineReferencesWithinTheOrder() {
    OrderItem line1 = item(1L, "OLR-DUP", "MLR-1");
    OrderItem line2 = item(2L, "OLR-DUP", "MLR-2");

    assertThrows(
        IllegalArgumentException.class, () -> order(List.of(line1, line2), eur(2000L), eur(420L)));
  }

  @Test
  void rejectsLineWithDifferentCurrencyThanOrder() {
    OrderItem euroLine = item(1L, "OLR-1", "MLR-1");
    Amount usdNet = new Amount(Currencies.USD.getValue(), 1000L);
    Amount usdTax = new Amount(Currencies.USD.getValue(), 210L);

    assertThrows(IllegalArgumentException.class, () -> order(List.of(euroLine), usdNet, usdTax));
  }

  @Test
  void rejectsNegativeOrderAmount() {
    OrderItem line = item(1L, "OLR-1", "MLR-1");

    assertThrows(IllegalArgumentException.class, () -> order(List.of(line), eur(-1L), eur(0L)));
  }

  @Test
  void rejectsAnOrderWithNoLines() {
    assertThrows(IllegalArgumentException.class, () -> order(List.of(), eur(0L), eur(0L)));
  }

  @Test
  void hasNoPspReferenceOrPaymentLinkBeforeThePspCreatesItsOrder() {
    Order order = order(List.of(item(1L, "OLR-1", "MLR-1")), eur(1000L), eur(210L));

    assertTrue(order.getPspReference().isEmpty());
    assertTrue(order.getPaymentLink().isEmpty());
  }

  @Test
  void rejectsBlankPaymentReference() {
    OrderItem line = item(1L, "OLR-1", "MLR-1");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            order(
                Countries.NETHERLANDS.getValue(),
                null,
                "request-fingerprint",
                " ",
                List.of(line),
                eur(1000L),
                eur(210L)));
  }

  @Test
  void rejectsBlankRequestFingerprint() {
    OrderItem line = item(1L, "OLR-1", "MLR-1");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            order(
                Countries.NETHERLANDS.getValue(),
                null,
                " ",
                "payment-ref",
                List.of(line),
                eur(1000L),
                eur(210L)));
  }

  @Test
  void rejectsShopperSubdivisionOutsideTheShopperCountry() {
    OrderItem line = item(1L, "OLR-1", "MLR-1");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            order(
                Countries.GERMANY.getValue(),
                CountrySubdivisions.US_CA.getValue(),
                "request-fingerprint",
                "payment-ref",
                List.of(line),
                eur(1000L),
                eur(210L)));
  }
}
