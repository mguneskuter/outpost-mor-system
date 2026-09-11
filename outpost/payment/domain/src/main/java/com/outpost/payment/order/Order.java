package com.outpost.payment.order;

import com.outpost.payment.common.Amount;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A payment order and the priced lines a merchant asked Outpost to collect. */
public final class Order {
  private final long orderId;
  private final String orderReference;
  private final String merchantReference;
  private final long accountId;
  private final long shopperId;
  private final Amount netAmount;
  private final Amount taxAmount;
  private final Amount grossAmount;
  private final String idempotencyKey;
  private final Instant createdAt;
  private final List<OrderItem> items;

  /**
   * Creates an order with its lines.
   *
   * @throws IllegalArgumentException when a line uses a different currency than the order, two
   *     lines share a reference, or the order's net, tax, or gross amount does not equal the sum of
   *     its lines.
   */
  public Order(
      long orderId,
      String orderReference,
      String merchantReference,
      long accountId,
      long shopperId,
      Amount netAmount,
      Amount taxAmount,
      Amount grossAmount,
      String idempotencyKey,
      Instant createdAt,
      List<OrderItem> items) {
    if (orderId <= 0) {
      throw new IllegalArgumentException("orderId must be positive: " + orderId);
    }
    this.orderId = orderId;
    if (orderReference == null || orderReference.isBlank()) {
      throw new IllegalArgumentException("orderReference must not be null or blank");
    }
    this.orderReference = orderReference;
    if (merchantReference == null || merchantReference.isBlank()) {
      throw new IllegalArgumentException("merchantReference must not be null or blank");
    }
    this.merchantReference = merchantReference;
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive: " + accountId);
    }
    this.accountId = accountId;
    if (shopperId <= 0) {
      throw new IllegalArgumentException("shopperId must be positive: " + shopperId);
    }
    this.shopperId = shopperId;
    this.netAmount = Objects.requireNonNull(netAmount, "netAmount");
    this.taxAmount = Objects.requireNonNull(taxAmount, "taxAmount");
    this.grossAmount = Objects.requireNonNull(grossAmount, "grossAmount");
    requireNonNegative(netAmount, "netAmount");
    requireNonNegative(taxAmount, "taxAmount");
    requireNonNegative(grossAmount, "grossAmount");
    if (!netAmount.currency().equals(taxAmount.currency())
        || !netAmount.currency().equals(grossAmount.currency())) {
      throw new IllegalArgumentException(
          "netAmount, taxAmount, and grossAmount must share one currency");
    }
    if (grossAmount.quantity() != netAmount.quantity() + taxAmount.quantity()) {
      throw new IllegalArgumentException("grossAmount must equal netAmount plus taxAmount");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("idempotencyKey must not be null or blank");
    }
    this.idempotencyKey = idempotencyKey;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(items, "items");
    if (items.isEmpty()) {
      throw new IllegalArgumentException("an order must carry at least one line");
    }
    requireDistinctReferences(items);
    requireLinesSumToOrderTotals(items, netAmount, taxAmount);
    this.items = List.copyOf(items);
  }

  private void requireNonNegative(Amount amount, String name) {
    if (amount.quantity() < 0) {
      throw new IllegalArgumentException(name + " must not be negative: " + amount);
    }
  }

  private void requireDistinctReferences(List<OrderItem> items) {
    Set<String> orderLineReferences = new HashSet<>();
    Set<String> merchantLineReferences = new HashSet<>();
    for (OrderItem item : items) {
      if (!item.getNetAmount().currency().equals(netAmount.currency())) {
        throw new IllegalArgumentException("every line must use the order's currency");
      }
      if (!orderLineReferences.add(item.getOrderLineReference())) {
        throw new IllegalArgumentException(
            "duplicate orderLineReference within the order: " + item.getOrderLineReference());
      }
      if (!merchantLineReferences.add(item.getMerchantLineReference())) {
        throw new IllegalArgumentException(
            "duplicate merchantLineReference within the order: " + item.getMerchantLineReference());
      }
    }
  }

  private void requireLinesSumToOrderTotals(
      List<OrderItem> items, Amount orderNetAmount, Amount orderTaxAmount) {
    long netSum = 0;
    long taxSum = 0;
    for (OrderItem item : items) {
      netSum = Math.addExact(netSum, item.getNetAmount().quantity());
      taxSum = Math.addExact(taxSum, item.getTaxAmount().quantity());
    }
    if (netSum != orderNetAmount.quantity() || taxSum != orderTaxAmount.quantity()) {
      throw new IllegalArgumentException(
          "order net and tax amounts must equal the sum of their lines");
    }
  }

  /** Returns the order identity. */
  public long getOrderId() {
    return orderId;
  }

  /** Returns Outpost's reference for this order. */
  public String getOrderReference() {
    return orderReference;
  }

  /** Returns the merchant's reference for this order. */
  public String getMerchantReference() {
    return merchantReference;
  }

  /** Returns the merchant account this order was placed against. */
  public long getAccountId() {
    return accountId;
  }

  /** Returns the shopper this order was created for. */
  public long getShopperId() {
    return shopperId;
  }

  /** Returns the net amount, the sum of the lines' net amounts. */
  public Amount getNetAmount() {
    return netAmount;
  }

  /** Returns the tax amount, the sum of the lines' tax amounts. */
  public Amount getTaxAmount() {
    return taxAmount;
  }

  /** Returns the gross amount, the net amount plus the tax amount. */
  public Amount getGrossAmount() {
    return grossAmount;
  }

  /** Returns the merchant-supplied key that deduplicates order creation. */
  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  /** Returns when the order was created. */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /** Returns the order's immutable lines. */
  public List<OrderItem> getItems() {
    return items;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Order that)) {
      return false;
    }
    return orderId == that.orderId
        && accountId == that.accountId
        && shopperId == that.shopperId
        && Objects.equals(orderReference, that.orderReference)
        && Objects.equals(merchantReference, that.merchantReference)
        && Objects.equals(netAmount, that.netAmount)
        && Objects.equals(taxAmount, that.taxAmount)
        && Objects.equals(grossAmount, that.grossAmount)
        && Objects.equals(idempotencyKey, that.idempotencyKey)
        && Objects.equals(createdAt, that.createdAt)
        && Objects.equals(items, that.items);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        orderId,
        orderReference,
        merchantReference,
        accountId,
        shopperId,
        netAmount,
        taxAmount,
        grossAmount,
        idempotencyKey,
        createdAt,
        items);
  }
}
