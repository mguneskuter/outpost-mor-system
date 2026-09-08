package com.outpost.accounting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AccountingCataloguesTest {
  @Test
  void registerTypesHaveTheApprovedPermanentIdsAndCodes() {
    assertEquals(
        Map.of(
            1L,
            "MERCHANT_PAYABLE",
            2L,
            "PAYOUT_PAYABLE",
            3L,
            "PSP_RECEIVABLE",
            4L,
            "TAX_PAYABLE",
            5L,
            "FEE_REVENUE",
            6L,
            "FX_CLEARING",
            7L,
            "FX_FEE_REVENUE"),
        catalogue(
            RegisterTypes.values(),
            type -> type.value().registerTypeId(),
            type -> type.value().code()));
  }

  @Test
  void transactionAndJournalEntryTypesHaveTheApprovedPermanentIdsAndCodes() {
    assertEquals(
        Map.of(1L, "PAYMENT", 2L, "CAPTURE", 3L, "REFUND"),
        catalogue(
            TransactionTypes.values(),
            type -> type.value().transactionTypeId(),
            type -> type.value().code()));
    assertEquals(
        Map.of(1L, "CAPTURE", 2L, "REFUND"),
        catalogue(
            JournalEntryTypes.values(),
            type -> type.value().journalEntryTypeId(),
            type -> type.value().code()));
  }

  @Test
  void transactionEventTypesHaveTheApprovedPermanentIdsCodesAndBookingRequirement() {
    assertEquals(
        Map.of(
            1L,
            "ORDER_CREATED",
            2L,
            "AUTHORISED",
            3L,
            "REFUSED",
            4L,
            "CANCELLED",
            5L,
            "CAPTURED",
            6L,
            "CAPTURE_FAILED",
            7L,
            "REFUND_REQUESTED",
            8L,
            "REFUND_ACCEPTED",
            9L,
            "REFUNDED",
            10L,
            "REFUND_FAILED"),
        catalogue(
            TransactionEventTypes.values(),
            type -> type.value().transactionEventTypeId(),
            type -> type.value().code()));
    assertTrue(TransactionEventTypes.CAPTURED.value().requiresJournalEntry());
    assertTrue(TransactionEventTypes.REFUNDED.value().requiresJournalEntry());
    assertEquals(
        2,
        Arrays.stream(TransactionEventTypes.values())
            .filter(type -> type.value().requiresJournalEntry())
            .count());
  }

  @Test
  @SuppressWarnings("NullAway")
  void cataloguesResolveOnlyExactCodes() {
    for (RegisterTypes type : RegisterTypes.values()) {
      assertSame(type.value(), RegisterTypes.fromCode(type.value().code()).orElseThrow());
    }
    assertFalse(RegisterTypes.fromCode("merchant_payable").isPresent());
    assertFalse(TransactionTypes.fromCode(" PAYMENT").isPresent());
    assertFalse(TransactionEventTypes.fromCode("CAPTURED ").isPresent());
    assertFalse(JournalEntryTypes.fromCode("UNKNOWN").isPresent());
    assertThrows(NullPointerException.class, () -> RegisterTypes.fromCode(null));
  }

  private static <E> Map<Long, String> catalogue(
      E[] values, Function<E, Long> identifier, Function<E, String> code) {
    return Arrays.stream(values).collect(Collectors.toMap(identifier, code));
  }
}
