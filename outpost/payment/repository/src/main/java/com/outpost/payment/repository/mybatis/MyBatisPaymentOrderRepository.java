package com.outpost.payment.repository.mybatis;

import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.payment.common.Amount;
import com.outpost.payment.common.ProductTypes;
import com.outpost.payment.common.ProductTypes.ProductType;
import com.outpost.payment.order.Order;
import com.outpost.payment.order.OrderItem;
import com.outpost.payment.repository.PaymentOrderRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** MyBatis implementation of {@link PaymentOrderRepository}. */
public final class MyBatisPaymentOrderRepository implements PaymentOrderRepository {
  private final PaymentOrderMapper mapper;

  /** Creates a repository over the generated mapper. */
  public MyBatisPaymentOrderRepository(PaymentOrderMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
  }

  @Override
  public Optional<Order> findByPaymentReference(String paymentReference) {
    OrderRow order = mapper.findOrder(paymentReference);
    if (order == null) {
      return Optional.empty();
    }
    Currency currency = currency(order.currencyId());
    List<OrderItem> items =
        mapper.findOrderItems(order.orderId()).stream().map(row -> toItem(row, currency)).toList();
    return Optional.of(
        new Order(
            order.orderId(),
            order.orderReference(),
            order.merchantReference(),
            order.accountId(),
            order.shopperId(),
            new Amount(currency, order.netAmount()),
            new Amount(currency, order.taxAmount()),
            new Amount(currency, order.grossAmount()),
            order.idempotencyKey(),
            order.createdAt(),
            items));
  }

  @Override
  public Optional<PspRouting> findPspRoutingByPaymentReference(String paymentReference) {
    PspRoutingRow row = mapper.findPspRouting(paymentReference);
    return Optional.ofNullable(row)
        .map(found -> new PspRouting(found.pspCode(), found.pspReference()));
  }

  private static OrderItem toItem(OrderItemRow row, Currency currency) {
    ProductType productType = productType(row.productTypeId());
    return new OrderItem(
        row.orderItemId(),
        row.sequence(),
        productType,
        row.orderLineReference(),
        row.merchantLineReference(),
        new Amount(currency, row.netAmount()),
        new Amount(currency, row.taxAmount()),
        row.taxRate());
  }

  private static Currency currency(long currencyId) {
    return Arrays.stream(Currencies.values())
        .map(Currencies::getValue)
        .filter(currency -> currency.getCurrencyId() == currencyId)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Unknown currency: " + currencyId));
  }

  private static ProductType productType(long productTypeId) {
    return Arrays.stream(ProductTypes.values())
        .map(ProductTypes::getValue)
        .filter(type -> type.getProductTypeId() == productTypeId)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Unknown product type: " + productTypeId));
  }
}
