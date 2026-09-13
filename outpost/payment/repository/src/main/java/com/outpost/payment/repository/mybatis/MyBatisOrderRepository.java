package com.outpost.payment.repository.mybatis;

import com.outpost.common.iso.Countries;
import com.outpost.common.iso.Countries.Country;
import com.outpost.common.iso.CountrySubdivisions;
import com.outpost.common.iso.CountrySubdivisions.CountrySubdivision;
import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.payment.common.Amount;
import com.outpost.payment.common.ProductTypes;
import com.outpost.payment.order.repository.OrderRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.ibatis.session.SqlSession;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Stores orders in {@code merchant_order}, {@code order_item}, and {@code shopper_detail}. */
public final class MyBatisOrderRepository implements OrderRepository {
  private final OrderMapper mapper;
  private final TransactionTemplate orderWrite;

  /**
   * Creates a repository over a Spring-managed {@code sqlSession}, so its statements join the
   * transactions {@code transactionManager} opens.
   */
  public MyBatisOrderRepository(
      SqlSession sqlSession, PlatformTransactionManager transactionManager) {
    this.mapper = sqlSession.getMapper(OrderMapper.class);
    this.orderWrite = new TransactionTemplate(transactionManager);
  }

  @Override
  public Optional<com.outpost.payment.order.Order> findOrderByIdempotencyKey(
      long accountId, String idempotencyKey) {
    return Optional.ofNullable(mapper.findOrderByIdempotencyKey(accountId, idempotencyKey))
        .map(this::withItems);
  }

  @Override
  public Optional<com.outpost.payment.order.Order> findOrderByOrderReference(
      long accountId, String orderReference) {
    return Optional.ofNullable(mapper.findOrderByOrderReference(accountId, orderReference))
        .map(this::withItems);
  }

  @Override
  public Optional<com.outpost.payment.order.Order> findOrderByPaymentReference(
      String paymentReference) {
    return Optional.ofNullable(mapper.findOrderByPaymentReference(paymentReference))
        .map(this::withItems);
  }

  @Override
  public Optional<PspRouting> findPspRoutingByPaymentReference(String paymentReference) {
    return Optional.ofNullable(mapper.findPspRoutingByPaymentReference(paymentReference));
  }

  @Override
  public Optional<com.outpost.payment.order.Order> insertOrder(
      com.outpost.payment.ShopperDetail shopper, com.outpost.payment.order.Order order) {
    // The shopper, order, and lines commit or roll back together only inside this Spring
    // transaction: outside one, each mapper statement commits alone on the pooled connection.
    return Objects.requireNonNull(
        orderWrite.execute(
            status -> {
              long shopperId = storedShopperId(shopper);
              Order storedOrder = mapper.insertOrder(toStored(order, shopperId));
              if (storedOrder == null) {
                status.setRollbackOnly();
                return Optional.<com.outpost.payment.order.Order>empty();
              }
              long orderId = Objects.requireNonNull(storedOrder.orderId(), "orderId");
              List<OrderItem> storedItems = new ArrayList<>();
              for (com.outpost.payment.order.OrderItem item : order.getItems()) {
                storedItems.add(mapper.insertOrderItem(toStored(item, orderId)));
              }
              return Optional.of(toOrder(storedOrder, storedItems));
            }));
  }

  @Override
  public void updateOrderPspReferenceAndPaymentLink(
      String paymentReference, String pspReference, String paymentLink) {
    mapper.updateOrderPspReferenceAndPaymentLink(paymentReference, pspReference, paymentLink);
  }

  private long storedShopperId(com.outpost.payment.ShopperDetail shopper) {
    ShopperDetail stored = mapper.insertShopperDetail(toStored(shopper));
    if (stored == null) {
      stored = mapper.findShopperDetailByEmail(shopper.getEmail());
    }
    if (stored == null) {
      throw new IllegalStateException("shopper was neither inserted nor found");
    }
    return Objects.requireNonNull(stored.shopperId(), "shopperId");
  }

  private com.outpost.payment.order.Order withItems(Order row) {
    return toOrder(row, mapper.findOrderItems(Objects.requireNonNull(row.orderId(), "orderId")));
  }

  private static ShopperDetail toStored(com.outpost.payment.ShopperDetail shopper) {
    return new ShopperDetail(
        null,
        shopper.getEmail(),
        shopper.getFullName(),
        shopper.getCountry().getIsoCode(),
        shopper.getCountrySubdivision().map(CountrySubdivision::getCode).orElse(null),
        shopper.getPostalCode().orElse(null));
  }

  private static Order toStored(com.outpost.payment.order.Order order, long shopperId) {
    return new Order(
        null,
        order.getOrderReference(),
        order.getMerchantReference(),
        order.getAccountId(),
        shopperId,
        order.getShopperCountry().getIsoCode(),
        order.getShopperCountrySubdivision().map(CountrySubdivision::getCode).orElse(null),
        order.getNetAmount().currency().getCurrencyCode(),
        order.getNetAmount().quantity(),
        order.getTaxAmount().quantity(),
        order.getGrossAmount().quantity(),
        order.getIdempotencyKey(),
        order.getRequestFingerprint(),
        order.getPaymentReference(),
        order.getPspAccountId(),
        order.getPspReference().orElse(null),
        order.getPaymentLink().orElse(null),
        order.getCreatedAt().orElse(null));
  }

  private static OrderItem toStored(com.outpost.payment.order.OrderItem item, long orderId) {
    return new OrderItem(
        null,
        orderId,
        item.getProductType().getCode(),
        item.getOrderLineReference(),
        item.getMerchantLineReference(),
        item.getNetAmount().quantity(),
        item.getTaxAmount().quantity(),
        item.getTaxRate());
  }

  private static com.outpost.payment.order.Order toOrder(Order row, List<OrderItem> items) {
    Currency currency =
        Currencies.fromCurrencyCode(row.currency())
            .orElseThrow(() -> new IllegalStateException("stored currency is not supported"));
    Country shopperCountry =
        Countries.fromIsoCode(row.shopperCountry())
            .orElseThrow(
                () -> new IllegalStateException("stored shopper country is not supported"));
    return new com.outpost.payment.order.Order(
        row.orderId(),
        row.orderReference(),
        row.merchantReference(),
        row.accountId(),
        row.shopperId(),
        shopperCountry,
        subdivision(shopperCountry, row.shopperCountrySubdivision()),
        new Amount(currency, row.netAmount()),
        new Amount(currency, row.taxAmount()),
        new Amount(currency, row.grossAmount()),
        row.idempotencyKey(),
        row.requestFingerprint(),
        row.paymentReference(),
        row.pspAccountId(),
        row.pspReference(),
        row.paymentLink(),
        row.createdAt(),
        items.stream().map(item -> toOrderItem(item, currency)).toList());
  }

  private static com.outpost.payment.order.OrderItem toOrderItem(OrderItem row, Currency currency) {
    return new com.outpost.payment.order.OrderItem(
        row.orderItemId(),
        ProductTypes.fromCode(row.productType())
            .orElseThrow(() -> new IllegalStateException("stored product type is not supported")),
        row.orderLineReference(),
        row.merchantLineReference(),
        new Amount(currency, row.netAmount()),
        new Amount(currency, row.taxAmount()),
        row.taxRate());
  }

  private static @Nullable CountrySubdivision subdivision(Country country, @Nullable String code) {
    if (code == null) {
      return null;
    }
    return CountrySubdivisions.fromCode(country, code)
        .orElseThrow(
            () -> new IllegalStateException("stored shopper subdivision is not supported"));
  }
}
