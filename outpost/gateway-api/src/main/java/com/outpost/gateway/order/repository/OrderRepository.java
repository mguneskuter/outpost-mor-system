package com.outpost.gateway.order.repository;

import com.outpost.gateway.order.service.OrderPhases;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Persistence boundary for merchant order creation. */
public interface OrderRepository {
  /** Reads the authenticated merchant account. */
  Optional<Merchant> findMerchant(long accountId);

  /** Finds an enabled, active PSP associated with a merchant. */
  Optional<Psp> findEnabledPsp(long merchantAccountId, String pspCode);

  /** Returns whether a merchant has fee terms for a currency. */
  boolean hasFeeConfiguration(long merchantAccountId, long currencyId);

  /** Loads an attempt by merchant idempotency key. */
  @Nullable PersistedOrder findByIdempotency(long merchantAccountId, String idempotencyKey);

  /** Persists the local order, lines, and payment as one durable phase. */
  @Nullable PersistedOrder insert(NewOrder order);

  /**
   * Claims a phase for one caller before it performs the remote operation.
   *
   * <p>The implementation must hold ownership in a database-session liveness mechanism for the
   * duration of the remote operation. A process termination therefore releases ownership without
   * allowing a live claimant to be overtaken.
   */
  boolean claimPhase(long orderId, OrderPhases phase, UUID claimToken);

  /** Releases a phase claim after the remote operation did not complete. */
  void releasePhaseClaim(long orderId, OrderPhases phase, UUID claimToken);

  /** Marks the Ledger phase as durable for its claimant. */
  void markLedgerCreated(long orderId, UUID claimToken);

  /** Stores PSP response facts and marks that phase as durable for its claimant. */
  void markPspCreated(long orderId, UUID claimToken, String pspReference, String paymentLink);

  /** Marks the complete response as durable for its claimant. */
  void markCompleted(long orderId, UUID claimToken);

  /** Merchant account facts needed by the Ledger request. */
  record Merchant(long accountId, String code) {}

  /** Enabled PSP facts needed by the PSP client and Ledger request. */
  record Psp(long accountId, String code) {}

  /** New local order facts. */
  record NewOrder(
      String orderReference,
      String merchantReference,
      long merchantAccountId,
      long shopperId,
      long currencyId,
      long netAmount,
      long taxAmount,
      long grossAmount,
      String idempotencyKey,
      String requestFingerprint,
      String paymentReference,
      long pspAccountId,
      Instant createdAt,
      Shopper shopper,
      List<Line> lines) {}

  /** Shopper facts written with the order. */
  record Shopper(
      String email,
      String fullName,
      long countryId,
      @Nullable Long subdivisionId,
      @Nullable String zipCode) {}

  /** Priced line facts written with the order. */
  record Line(
      int sequence,
      long productTypeId,
      String orderLineReference,
      String merchantLineReference,
      long netAmount,
      long taxAmount,
      String taxRate) {}

  /** Reconstructable local order attempt. */
  record PersistedOrder(
      long orderId,
      String orderReference,
      String merchantReference,
      long merchantAccountId,
      String merchantCode,
      long shopperId,
      String shopperCountry,
      @Nullable String shopperCountrySubdivision,
      String paymentShopperCountry,
      @Nullable String paymentShopperCountrySubdivision,
      long currencyId,
      String currency,
      long netAmount,
      long taxAmount,
      long grossAmount,
      String idempotencyKey,
      String requestFingerprint,
      String paymentReference,
      long pspAccountId,
      String pspCode,
      @Nullable String pspReference,
      @Nullable String paymentLink,
      Instant createdAt,
      OrderPhases phase,
      List<Line> lines) {}
}
