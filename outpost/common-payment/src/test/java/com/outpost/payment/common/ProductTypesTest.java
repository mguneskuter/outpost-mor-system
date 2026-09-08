package com.outpost.payment.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.outpost.payment.common.ProductTypes.ProductType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ProductTypesTest {
  @Test
  void containsExactlyTheSupportedCatalogue() {
    Map<Long, String> catalogue =
        Arrays.stream(ProductTypes.values())
            .collect(
                Collectors.toMap(
                    type -> type.value().productTypeId(), type -> type.value().code()));

    assertEquals(Map.of(1L, "DIGITAL_GOODS", 2L, "PHYSICAL_GOODS"), catalogue);
    assertEquals(
        Set.of("DIGITAL_GOODS", "PHYSICAL_GOODS"),
        Arrays.stream(ProductTypes.values())
            .map(type -> type.value().code())
            .collect(Collectors.toSet()));
  }

  @Test
  void valuesAreImmutableAndEnumOwned() {
    for (ProductTypes productType : ProductTypes.values()) {
      ProductType value = productType.value();
      assertTrue(value.productTypeId() > 0);
      assertFalse(value.code().isBlank());
      assertSame(value, productType.value());
      assertTrue(
          Arrays.stream(value.getClass().getDeclaredConstructors())
              .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers())));
    }
  }

  @Test
  @SuppressWarnings("NullAway")
  void resolvesOnlyExactSupportedCodes() {
    assertSame(
        ProductTypes.DIGITAL_GOODS.value(), ProductTypes.fromCode("DIGITAL_GOODS").orElseThrow());
    assertSame(
        ProductTypes.PHYSICAL_GOODS.value(), ProductTypes.fromCode("PHYSICAL_GOODS").orElseThrow());
    assertTrue(ProductTypes.fromCode("digital_goods").isEmpty());
    assertTrue(ProductTypes.fromCode(" DIGITAL_GOODS").isEmpty());
    assertTrue(ProductTypes.fromCode("DIGITAL_GOODS ").isEmpty());
    assertTrue(ProductTypes.fromCode("").isEmpty());
    assertTrue(ProductTypes.fromCode("unknown").isEmpty());
    assertThrows(NullPointerException.class, () -> ProductTypes.fromCode(null));
  }

  @Test
  void hasNoAccessibleProductTypeConstructionPath() {
    assertDoesNotThrow(
        () -> {
          for (Constructor<?> constructor : ProductType.class.getDeclaredConstructors()) {
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
          }
        });
  }
}
