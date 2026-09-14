package com.outpost.backoffice.payment;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One order and what Outpost knows about its payment.
 *
 * @param pspReference the PSP's reference, absent while the PSP has not accepted the order
 * @param lastEvent the code of the last event the Ledger booked on the payment or its capture or
 *     refund, absent while nothing is booked
 * @param platformFee the fee the Ledger booked as pending on the payment, in minor units, absent
 *     while the payment is not booked
 * @param refundable whether the payment is captured and a line of the order has no refund that has
 *     not failed
 */
public record Payment(
    String orderReference,
    @Nullable String pspReference,
    String merchantCode,
    String merchantName,
    String pspName,
    @Nullable String lastEvent,
    String currency,
    long grossAmount,
    long netAmount,
    long taxAmount,
    @Nullable Long platformFee,
    String shopperCountry,
    List<String> goodsTypes,
    Instant createdAt,
    boolean refundable) {
  /** Copies the goods types so the payment stays immutable. */
  public Payment {
    goodsTypes = List.copyOf(goodsTypes);
  }
}
