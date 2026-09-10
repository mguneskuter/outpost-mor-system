package com.outpost.payment.common;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProductTypesTest {
  @Test
  @SuppressWarnings("NullAway")
  void resolvesOnlyExactSupportedCodes() {
    assertSame(
        ProductTypes.DIGITAL_GOODS.getValue(),
        ProductTypes.fromCode("DIGITAL_GOODS").orElseThrow());
    assertTrue(ProductTypes.fromCode("digital_goods").isEmpty());
    assertTrue(ProductTypes.fromCode(" DIGITAL_GOODS").isEmpty());
    assertTrue(ProductTypes.fromCode("DIGITAL_GOODS ").isEmpty());
    assertTrue(ProductTypes.fromCode("").isEmpty());
    assertTrue(ProductTypes.fromCode("unknown").isEmpty());
    assertThrows(NullPointerException.class, () -> ProductTypes.fromCode(null));
  }
}
