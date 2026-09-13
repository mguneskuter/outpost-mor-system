package com.outpost.payment.order;

import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.payment.common.Amount;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A payment order, the priced lines a merchant asked Outpost to collect, and the payment that
 * collects them.
 */
public final class Order {
  @Nullable private final Long orderId;
  private final String orderReference;
  private final String merchantReference;
  private final long accountId;
  @Nullable private final Long shopperId;
  private final Country shopperCountry;
  @Nullable private final CountrySubdivision shopperCountrySubdivision;
  private final Amount netAmount;
  private final Amount taxAmount;
  private final Amount grossAmount;
  private final String idempotencyKey;
  private final String requestFingerprint;
  private final long pspAccountId;
  @Nullable private final String pspReference;
  @Nullable private final String paymentLink;
  @Nullable private final Instant createdAt;
  private final List<OrderItem> items;

  /**
   * Creates an order with its lines. {@code shopperCountry} and {@code shopperCountrySubdivision}
   * are the jurisdiction the order was sold under; a null subdivision means a country-level
   * jurisdiction. {@code pspReference} and {@code paymentLink} are absent until the PSP has created
   * its order for {@code orderReference}. {@code orderId}, {@code shopperId}, {@code createdAt},
   * and the lines' ids are absent until the order is stored.
   *
   * @throws IllegalArgumentException when a text value is blank, the subdivision is not in the
   *     shopper country, a line uses a different currency than the order, two lines share a
   *     reference, or the order's net, tax, or gross amount does not equal the sum of its lines.
   */
  public Order(
      @Nullable Long orderId,
      String orderReference,
      String merchantReference,
      long accountId,
      @Nullable Long shopperId,
      Country shopperCountry,
      @Nullable CountrySubdivision shopperCountrySubdivision,
      Amount netAmount,
      Amount taxAmount,
      Amount grossAmount,
      String idempotencyKey,
      String requestFingerprint,
      long pspAccountId,
      @Nullable String pspReference,
      @Nullable String paymentLink,
      @Nullable Instant createdAt,
      List<OrderItem> items) {
    if (orderId != null && orderId <= 0) {
      throw new IllegalArgumentException("orderId must be positive: " + orderId);
    }
    this.orderId = orderId;
    this.orderReference = requireText(orderReference, "orderReference");
    this.merchantReference = requireText(merchantReference, "merchantReference");
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive: " + accountId);
    }
    this.accountId = accountId;
    if (shopperId != null && shopperId <= 0) {
      throw new IllegalArgumentException("shopperId must be positive: " + shopperId);
    }
    this.shopperId = shopperId;
    this.shopperCountry = Objects.requireNonNull(shopperCountry, "shopperCountry");
    if (shopperCountrySubdivision != null
        && !shopperCountrySubdivision.getCountry().equals(shopperCountry)) {
      throw new IllegalArgumentException("shopperCountrySubdivision must belong to shopperCountry");
    }
    this.shopperCountrySubdivision = shopperCountrySubdivision;
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
    this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
    this.requestFingerprint = requireText(requestFingerprint, "requestFingerprint");
    if (pspAccountId <= 0) {
      throw new IllegalArgumentException("pspAccountId must be positive: " + pspAccountId);
    }
    this.pspAccountId = pspAccountId;
    if (pspReference != null && pspReference.isBlank()) {
      throw new IllegalArgumentException("pspReference must not be blank when supplied");
    }
    this.pspReference = pspReference;
    if (paymentLink != null && paymentLink.isBlank()) {
      throw new IllegalArgumentException("paymentLink must not be blank when supplied");
    }
    this.paymentLink = paymentLink;
    this.createdAt = createdAt;
    Objects.requireNonNull(items, "items");
    if (items.isEmpty()) {
      throw new IllegalArgumentException("an order must carry at least one line");
    }
    requireDistinctReferences(items);
    requireLinesSumToOrderTotals(netAmount, taxAmount, items);
    this.items = List.copyOf(items);
  }

  private static void requireLinesSumToOrderTotals(
      Amount netAmount, Amount taxAmount, List<OrderItem> items) {
    long netSum = 0;
    long taxSum = 0;
    for (OrderItem item : items) {
      netSum = Math.addExact(netSum, item.getNetAmount().quantity());
      taxSum = Math.addExact(taxSum, item.getTaxAmount().quantity());
    }
    if (netSum != netAmount.quantity() || taxSum != taxAmount.quantity()) {
      throw new IllegalArgumentException(
          "order net and tax amounts must equal the sum of their lines");
    }
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be null or blank");
    }
    return value;
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

  /** Returns the order identity; empty until the order is stored. */
  public OptionalLong getOrderId() {
    return orderId == null ? OptionalLong.empty() : OptionalLong.of(orderId);
  }

  /**
   * Returns Outpost's reference for this order: the original reference the Ledger's PAYMENT
   * transaction and the PSP carry.
   */
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

  /** Returns the shopper this order was created for; empty until the order is stored. */
  public OptionalLong getShopperId() {
    return shopperId == null ? OptionalLong.empty() : OptionalLong.of(shopperId);
  }

  /** Returns the country of the jurisdiction this order was sold under. */
  public Country getShopperCountry() {
    return shopperCountry;
  }

  /**
   * Returns the subdivision of the jurisdiction this order was sold under; empty means a
   * country-level jurisdiction.
   */
  public Optional<CountrySubdivision> getShopperCountrySubdivision() {
    return Optional.ofNullable(shopperCountrySubdivision);
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

  /**
   * Returns the fingerprint of the creation request, which tells a repeated request from a
   * different one under the same idempotency key.
   */
  public String getRequestFingerprint() {
    return requestFingerprint;
  }

  /** Returns the PSP account this order's payment is routed to. */
  public long getPspAccountId() {
    return pspAccountId;
  }

  /** Returns the PSP's own reference, once the PSP has created its order. */
  public Optional<String> getPspReference() {
    return Optional.ofNullable(pspReference);
  }

  /** Returns the link the shopper pays through, once the PSP has created its order. */
  public Optional<String> getPaymentLink() {
    return Optional.ofNullable(paymentLink);
  }

  /** Returns when the order was stored; empty until the order is stored. */
  public Optional<Instant> getCreatedAt() {
    return Optional.ofNullable(createdAt);
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
    return Objects.equals(orderId, that.orderId)
        && accountId == that.accountId
        && Objects.equals(shopperId, that.shopperId)
        && pspAccountId == that.pspAccountId
        && Objects.equals(orderReference, that.orderReference)
        && Objects.equals(merchantReference, that.merchantReference)
        && Objects.equals(shopperCountry, that.shopperCountry)
        && Objects.equals(shopperCountrySubdivision, that.shopperCountrySubdivision)
        && Objects.equals(netAmount, that.netAmount)
        && Objects.equals(taxAmount, that.taxAmount)
        && Objects.equals(grossAmount, that.grossAmount)
        && Objects.equals(idempotencyKey, that.idempotencyKey)
        && Objects.equals(requestFingerprint, that.requestFingerprint)
        && Objects.equals(pspReference, that.pspReference)
        && Objects.equals(paymentLink, that.paymentLink)
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
        shopperCountry,
        shopperCountrySubdivision,
        netAmount,
        taxAmount,
        grossAmount,
        idempotencyKey,
        requestFingerprint,
        pspAccountId,
        pspReference,
        paymentLink,
        createdAt,
        items);
  }
}
