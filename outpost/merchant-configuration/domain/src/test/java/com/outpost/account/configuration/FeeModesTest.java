package com.outpost.account.configuration;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FeeModesTest {
  @Test
  @SuppressWarnings("NullAway")
  void resolvesOnlyExactCodes() {
    assertSame(FeeModes.PERCENTAGE.getValue(), FeeModes.fromCode("PERCENTAGE").orElseThrow());
    assertTrue(FeeModes.fromCode("percentage").isEmpty());
    assertTrue(FeeModes.fromCode(" PERCENTAGE").isEmpty());
    assertTrue(FeeModes.fromCode("PERCENTAGE ").isEmpty());
    assertTrue(FeeModes.fromCode("unknown").isEmpty());
    assertThrows(NullPointerException.class, () -> FeeModes.fromCode(null));
  }
}
