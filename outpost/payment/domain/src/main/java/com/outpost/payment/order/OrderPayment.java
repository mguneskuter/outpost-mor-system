package com.outpost.payment.order;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** The mapping from an order to the payment reference Outpost sent to the ledger and the PSP. */
public final class OrderPayment {
  private final long orderPaymentId;
  private final long orderId;
  private final String paymentReference;
  private final long pspAccountId;
  @Nullable private final String pspReference;
  private final Instant createdAt;

  /**
   * Creates an order payment. {@code pspReference} is absent until the PSP confirms the payment it
   * was asked to create.
   */
  public OrderPayment(
      long orderPaymentId,
      long orderId,
      String paymentReference,
      long pspAccountId,
      @Nullable String pspReference,
      Instant createdAt) {
    if (orderPaymentId <= 0) {
      throw new IllegalArgumentException("orderPaymentId must be positive: " + orderPaymentId);
    }
    this.orderPaymentId = orderPaymentId;
    if (orderId <= 0) {
      throw new IllegalArgumentException("orderId must be positive: " + orderId);
    }
    this.orderId = orderId;
    if (paymentReference == null || paymentReference.isBlank()) {
      throw new IllegalArgumentException("paymentReference must not be null or blank");
    }
    this.paymentReference = paymentReference;
    if (pspAccountId <= 0) {
      throw new IllegalArgumentException("pspAccountId must be positive: " + pspAccountId);
    }
    this.pspAccountId = pspAccountId;
    if (pspReference != null && pspReference.isBlank()) {
      throw new IllegalArgumentException("pspReference must not be blank when supplied");
    }
    this.pspReference = pspReference;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  /** Returns the order payment identity. */
  public long getOrderPaymentId() {
    return orderPaymentId;
  }

  /** Returns the order this payment was created for. */
  public long getOrderId() {
    return orderId;
  }

  /** Returns the reference Outpost passed to the ledger as {@code Transaction.reference}. */
  public String getPaymentReference() {
    return paymentReference;
  }

  /** Returns the PSP account this payment was routed to. */
  public long getPspAccountId() {
    return pspAccountId;
  }

  /** Returns the PSP's own reference, once the PSP has confirmed the payment. */
  public Optional<String> getPspReference() {
    return Optional.ofNullable(pspReference);
  }

  /** Returns when the order payment was created. */
  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof OrderPayment that)) {
      return false;
    }
    return orderPaymentId == that.orderPaymentId
        && orderId == that.orderId
        && pspAccountId == that.pspAccountId
        && Objects.equals(paymentReference, that.paymentReference)
        && Objects.equals(pspReference, that.pspReference)
        && Objects.equals(createdAt, that.createdAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        orderPaymentId, orderId, paymentReference, pspAccountId, pspReference, createdAt);
  }
}
