package com.outpost.payment;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class PspEventStaticDataTest {
  @Test
  void exposesEventCodesWithStableIds() {
    assertArrayEquals(
        new long[] {1L, 2L, 3L, 4L},
        java.util.Arrays.stream(PspEventCodes.values())
            .mapToLong(value -> value.getValue().getPspEventCodeId())
            .toArray());
    assertArrayEquals(
        new String[] {"AUTHORISATION", "CAPTURE", "REFUND", "CANCELLATION"},
        java.util.Arrays.stream(PspEventCodes.values())
            .map(value -> value.getValue().getCode())
            .toArray(String[]::new));
  }

  @Test
  void exposesEventStatusesWithStableIds() {
    assertArrayEquals(
        new long[] {1L, 2L, 3L},
        java.util.Arrays.stream(PspEventStatuses.values())
            .mapToLong(value -> value.getValue().getPspEventStatusId())
            .toArray());
    assertArrayEquals(
        new String[] {"RECEIVED", "IN_PROGRESS", "DONE"},
        java.util.Arrays.stream(PspEventStatuses.values())
            .map(value -> value.getValue().getCode())
            .toArray(String[]::new));
  }

  @Test
  void exposesEventResultsWithStableIds() {
    assertArrayEquals(
        new long[] {1L, 2L},
        java.util.Arrays.stream(PspEventResults.values())
            .mapToLong(value -> value.getValue().getPspEventResultId())
            .toArray());
    assertArrayEquals(
        new String[] {"SUCCESS", "FAILED"},
        java.util.Arrays.stream(PspEventResults.values())
            .map(value -> value.getValue().getCode())
            .toArray(String[]::new));
  }
}
