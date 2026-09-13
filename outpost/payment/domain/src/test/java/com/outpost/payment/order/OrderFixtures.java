package com.outpost.payment.order;

import com.outpost.account.Account;
import com.outpost.account.AccountTypes;
import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.common.iso.Currencies;
import com.outpost.payment.common.Amount;
import com.outpost.payment.common.ProductTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class OrderFixtures {
  static final Instant CREATED = Instant.parse("2026-02-01T00:00:00Z");
  private static final Account ROOT =
      Account.of(1L, AccountTypes.ROOT.getValue(), "ROOT", "Root", true, CREATED, null);
  static final Account MERCHANT =
      Account.of(
          100L, AccountTypes.MERCHANT.getValue(), "MERCHANT", "Merchant", true, CREATED, ROOT);
  static final Account PSP =
      Account.of(300L, AccountTypes.PSP.getValue(), "PSP", "PSP", true, CREATED, ROOT);

  private OrderFixtures() {}

  static Amount eur(long quantity) {
    return new Amount(Currencies.EUR.getValue(), quantity);
  }

  static OrderItem item(long orderItemId, String orderLineReference, String merchantLineReference) {
    return orderItem(orderItemId, orderLineReference, merchantLineReference);
  }

  static OrderItem unsavedItem(String orderLineReference, String merchantLineReference) {
    return orderItem(null, orderLineReference, merchantLineReference);
  }

  private static OrderItem orderItem(
      @Nullable Long orderItemId, String orderLineReference, String merchantLineReference) {
    return new OrderItem(
        orderItemId,
        ProductTypes.DIGITAL_GOODS.getValue(),
        orderLineReference,
        merchantLineReference,
        eur(1000L),
        eur(210L),
        new BigDecimal("0.21"));
  }

  static Order order(List<OrderItem> items, Amount netAmount, Amount taxAmount) {
    return order(
        Countries.NETHERLANDS.getValue(), null, "request-fingerprint", items, netAmount, taxAmount);
  }

  static Order order(
      Country shopperCountry,
      @Nullable CountrySubdivision shopperCountrySubdivision,
      String requestFingerprint,
      List<OrderItem> items,
      Amount netAmount,
      Amount taxAmount) {
    return new Order(
        1L,
        "order-ref",
        "merchant-ref",
        MERCHANT,
        200L,
        shopperCountry,
        shopperCountrySubdivision,
        netAmount,
        taxAmount,
        netAmount.plus(taxAmount),
        "idempotency-key",
        requestFingerprint,
        PSP,
        null,
        null,
        CREATED,
        items);
  }

  static Order unsavedOrder(List<OrderItem> items, Amount netAmount, Amount taxAmount) {
    return new Order(
        null,
        "order-ref",
        "merchant-ref",
        MERCHANT,
        null,
        Countries.NETHERLANDS.getValue(),
        null,
        netAmount,
        taxAmount,
        netAmount.plus(taxAmount),
        "idempotency-key",
        "request-fingerprint",
        PSP,
        null,
        null,
        CREATED,
        items);
  }
}
