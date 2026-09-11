package com.outpost.payment.order;

import com.outpost.payment.common.Amount;
import com.outpost.payment.common.ProductTypes.ProductType;
import java.math.BigDecimal;
import java.util.Objects;

/** One priced line of a payment order, carrying the tax rate applied to it. */
public final class OrderItem {
  private final long orderItemId;
  private final int sequence;
  private final ProductType productType;
  private final String orderLineReference;
  private final String merchantLineReference;
  private final Amount netAmount;
  private final Amount taxAmount;
  private final BigDecimal taxRate;

  /** Creates an order item. */
  public OrderItem(
      long orderItemId,
      int sequence,
      ProductType productType,
      String orderLineReference,
      String merchantLineReference,
      Amount netAmount,
      Amount taxAmount,
      BigDecimal taxRate) {
    if (orderItemId <= 0) {
      throw new IllegalArgumentException("orderItemId must be positive: " + orderItemId);
    }
    this.orderItemId = orderItemId;
    if (sequence <= 0) {
      throw new IllegalArgumentException("sequence must be positive: " + sequence);
    }
    this.sequence = sequence;
    this.productType = Objects.requireNonNull(productType, "productType");
    if (orderLineReference == null || orderLineReference.isBlank()) {
      throw new IllegalArgumentException("orderLineReference must not be null or blank");
    }
    this.orderLineReference = orderLineReference;
    if (merchantLineReference == null || merchantLineReference.isBlank()) {
      throw new IllegalArgumentException("merchantLineReference must not be null or blank");
    }
    this.merchantLineReference = merchantLineReference;
    this.netAmount = Objects.requireNonNull(netAmount, "netAmount");
    if (netAmount.quantity() < 0) {
      throw new IllegalArgumentException("netAmount must not be negative: " + netAmount);
    }
    this.taxAmount = Objects.requireNonNull(taxAmount, "taxAmount");
    if (taxAmount.quantity() < 0) {
      throw new IllegalArgumentException("taxAmount must not be negative: " + taxAmount);
    }
    if (!netAmount.currency().equals(taxAmount.currency())) {
      throw new IllegalArgumentException("netAmount and taxAmount must use the same currency");
    }
    this.taxRate = Objects.requireNonNull(taxRate, "taxRate");
    if (taxRate.signum() < 0) {
      throw new IllegalArgumentException("taxRate must not be negative: " + taxRate);
    }
  }

  /** Returns the line identity. */
  public long getOrderItemId() {
    return orderItemId;
  }

  /** Returns the line's position within the order. */
  public int getSequence() {
    return sequence;
  }

  /** Returns the line's goods type. */
  public ProductType getProductType() {
    return productType;
  }

  /** Returns Outpost's reference for this line. */
  public String getOrderLineReference() {
    return orderLineReference;
  }

  /** Returns the merchant's reference for this line. */
  public String getMerchantLineReference() {
    return merchantLineReference;
  }

  /** Returns the net amount, in minor units. */
  public Amount getNetAmount() {
    return netAmount;
  }

  /** Returns the tax amount, in minor units. */
  public Amount getTaxAmount() {
    return taxAmount;
  }

  /** Returns the tax rate resolved for this line's jurisdiction and goods type. */
  public BigDecimal getTaxRate() {
    return taxRate;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof OrderItem that)) {
      return false;
    }
    return orderItemId == that.orderItemId
        && sequence == that.sequence
        && Objects.equals(productType, that.productType)
        && Objects.equals(orderLineReference, that.orderLineReference)
        && Objects.equals(merchantLineReference, that.merchantLineReference)
        && Objects.equals(netAmount, that.netAmount)
        && Objects.equals(taxAmount, that.taxAmount)
        && Objects.equals(taxRate, that.taxRate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        orderItemId,
        sequence,
        productType,
        orderLineReference,
        merchantLineReference,
        netAmount,
        taxAmount,
        taxRate);
  }
}
