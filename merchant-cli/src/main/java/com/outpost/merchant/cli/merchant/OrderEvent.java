package com.outpost.merchant.cli.merchant;

import java.time.Instant;

/**
 * One event the Ledger booked for an order: on its payment, or on a capture or refund of that
 * payment.
 *
 * @param transactionType PAYMENT, CAPTURE, or REFUND
 * @param eventType the event's code, such as AUTHORISED, REFUSED, CAPTURED, or REFUNDED
 * @param occurredAt when the Ledger booked it
 */
public record OrderEvent(String transactionType, String eventType, Instant occurredAt) {}
