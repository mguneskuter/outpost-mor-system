package com.outpost.gateway.order.repository.mybatis;

import com.outpost.framework.persistence.RegisteredMapper;
import com.outpost.gateway.order.repository.OrderRepository.Line;
import com.outpost.gateway.order.repository.OrderRepository.NewOrder;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/** MyBatis statements for local order creation. */
@RegisteredMapper
public interface OrderMapper {
  /** Finds an active or inactive merchant account by id. */
  @Nullable MerchantRow findMerchant(@Param("accountId") long accountId);

  /** Finds a merchant's PSP account by provider code. */
  @Nullable PspRow findEnabledPsp(
      @Param("merchantAccountId") long merchantAccountId, @Param("pspCode") String pspCode);

  /** Checks whether fee terms exist for a merchant and currency. */
  boolean hasFeeConfiguration(
      @Param("merchantAccountId") long merchantAccountId, @Param("currencyId") long currencyId);

  /** Finds an order by its merchant-scoped idempotency key. */
  @Nullable OrderRow findByIdempotency(
      @Param("merchantAccountId") long merchantAccountId,
      @Param("idempotencyKey") String idempotencyKey);

  /** Upserts shopper details and returns the shopper id. */
  @Nullable Long insertShopper(@Param("order") NewOrder order);

  /** Inserts the order unless another request used the idempotency key first. */
  @Nullable Long insertOrder(@Param("order") NewOrder order);

  /** Inserts one priced order line. */
  int insertLine(@Param("orderId") long orderId, @Param("line") Line line);

  /** Inserts the order payment. */
  int insertPayment(@Param("orderId") long orderId, @Param("order") NewOrder order);

  /** Finds an order by its durable id. */
  @Nullable OrderRow findById(@Param("orderId") long orderId);

  /** Finds the order's lines in sequence order. */
  List<OrderLineRow> findLines(@Param("orderId") long orderId);

  /** Claims an order phase for one caller. */
  int claimPhase(
      @Param("orderId") long orderId,
      @Param("phase") String phase,
      @Param("claimToken") UUID claimToken);

  /** Releases an order phase claim. */
  int releasePhaseClaim(
      @Param("orderId") long orderId,
      @Param("phase") String phase,
      @Param("claimToken") UUID claimToken);

  /** Advances an order after Ledger accepts the payment. */
  int markLedgerCreated(@Param("orderId") long orderId, @Param("claimToken") UUID claimToken);

  /** Advances an order after PSP facts are stored. */
  int markPspCreated(
      @Param("orderId") long orderId,
      @Param("claimToken") UUID claimToken,
      @Param("pspReference") String pspReference,
      @Param("paymentLink") String paymentLink);

  /** Stores PSP response facts before advancing the order phase. */
  int storePspFacts(
      @Param("orderId") long orderId,
      @Param("claimToken") UUID claimToken,
      @Param("pspReference") String pspReference,
      @Param("paymentLink") String paymentLink);

  /** Advances an order after the PSP phase is complete. */
  int markCompleted(@Param("orderId") long orderId, @Param("claimToken") UUID claimToken);

  /** Persistence projection of a merchant account. */
  record MerchantRow(long accountId, String code, boolean active) {}

  /** Persistence projection of a PSP account. */
  record PspRow(long accountId, String code, boolean active) {}
}
