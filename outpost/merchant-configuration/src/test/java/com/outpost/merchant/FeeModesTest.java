package com.outpost.merchant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.outpost.merchant.FeeModes.FeeMode;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FeeModesTest {
  @Test
  void containsExactlyTheSupportedCatalogue() {
    Map<Long, String> catalogue =
        Arrays.stream(FeeModes.values())
            .collect(
                Collectors.toMap(mode -> mode.value().feeModeId(), mode -> mode.value().code()));

    assertEquals(Map.of(1L, "PERCENTAGE", 2L, "PERCENTAGE_PLUS_FIXED"), catalogue);
  }

  @Test
  void valuesAreImmutableAndEnumOwned() {
    for (FeeModes feeMode : FeeModes.values()) {
      FeeMode value = feeMode.value();
      assertTrue(value.feeModeId() > 0);
      assertFalse(value.code().isBlank());
      assertSame(value, feeMode.value());
      assertTrue(
          Arrays.stream(value.getClass().getDeclaredConstructors())
              .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers())));
    }
  }

  @Test
  @SuppressWarnings("NullAway")
  void resolvesOnlyExactCodes() {
    assertSame(FeeModes.PERCENTAGE.value(), FeeModes.fromCode("PERCENTAGE").orElseThrow());
    assertSame(
        FeeModes.PERCENTAGE_PLUS_FIXED.value(),
        FeeModes.fromCode("PERCENTAGE_PLUS_FIXED").orElseThrow());
    assertTrue(FeeModes.fromCode("percentage").isEmpty());
    assertTrue(FeeModes.fromCode(" PERCENTAGE").isEmpty());
    assertTrue(FeeModes.fromCode("PERCENTAGE ").isEmpty());
    assertTrue(FeeModes.fromCode("unknown").isEmpty());
    assertThrows(NullPointerException.class, () -> FeeModes.fromCode(null));
  }
}
