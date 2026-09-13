package com.outpost.payment.order;

import static com.outpost.payment.order.OrderFixtures.eur;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.outpost.payment.common.ProductTypes;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderItemTest {
  @Test
  void anUnsavedItemHasNoId() {
    OrderItem item =
        new OrderItem(
            null,
            ProductTypes.DIGITAL_GOODS.getValue(),
            "OLR-1",
            "MLR-1",
            eur(1000L),
            eur(210L),
            new BigDecimal("0.21"));

    assertTrue(item.getOrderItemId().isEmpty());
  }

  @Test
  void rejectsNegativeNetAmount() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OrderItem(
                1L,
                ProductTypes.DIGITAL_GOODS.getValue(),
                "OLR-1",
                "MLR-1",
                eur(-1L),
                eur(0L),
                new BigDecimal("0.21")));
  }

  @Test
  void rejectsBlankMerchantLineReference() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OrderItem(
                1L,
                ProductTypes.DIGITAL_GOODS.getValue(),
                "OLR-1",
                " ",
                eur(1000L),
                eur(210L),
                new BigDecimal("0.21")));
  }

  @Test
  void rejectsNegativeTaxRate() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OrderItem(
                1L,
                ProductTypes.DIGITAL_GOODS.getValue(),
                "OLR-1",
                "MLR-1",
                eur(1000L),
                eur(210L),
                new BigDecimal("-0.01")));
  }
}
