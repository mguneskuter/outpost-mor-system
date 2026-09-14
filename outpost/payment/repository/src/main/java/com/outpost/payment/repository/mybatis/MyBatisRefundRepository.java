package com.outpost.payment.repository.mybatis;

import com.outpost.common.iso.Currencies;
import com.outpost.common.iso.Currencies.Currency;
import com.outpost.payment.refund.repository.RefundRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.ibatis.session.SqlSession;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stores merchant refunds in {@code merchant_refund} and the lines they claim in {@code
 * refund_item}.
 */
public final class MyBatisRefundRepository implements RefundRepository {
  private final RefundMapper mapper;
  private final TransactionTemplate refundWrite;

  /**
   * Creates a repository over a Spring-managed {@code sqlSession}, so its statements join the
   * transactions {@code transactionManager} opens.
   */
  public MyBatisRefundRepository(
      SqlSession sqlSession, PlatformTransactionManager transactionManager) {
    this.mapper = sqlSession.getMapper(RefundMapper.class);
    this.refundWrite = new TransactionTemplate(transactionManager);
  }

  @Override
  public Optional<com.outpost.payment.refund.Refund> insertRefund(
      com.outpost.payment.refund.Refund refund) {
    // Claim every line or none — the refund and its items commit or roll back together only
    // inside this Spring transaction: outside one, each mapper statement commits alone on the
    // pooled connection.
    return Objects.requireNonNull(
        refundWrite.execute(
            status -> {
              Refund storedRefund = mapper.insertRefund(refund);
              long refundId = Objects.requireNonNull(storedRefund.refundId(), "refundId");
              List<RefundItem> storedItems = new ArrayList<>();
              // Claim in line order, so two refunds naming the same lines wait on each other in
              // one direction and the loser is refused rather than deadlocked.
              for (long orderItemId : claimedLineIds(refund)) {
                RefundItem storedItem = mapper.insertRefundItem(refundId, orderItemId);
                if (storedItem == null) {
                  status.setRollbackOnly();
                  return Optional.<com.outpost.payment.refund.Refund>empty();
                }
                storedItems.add(storedItem);
              }
              return Optional.of(toRefund(storedRefund, storedItems));
            }));
  }

  @Override
  public Optional<com.outpost.payment.refund.Refund> findRefundByRefundReference(
      String refundReference) {
    return Optional.ofNullable(mapper.findRefundByRefundReference(refundReference))
        .map(this::toFoundRefund);
  }

  @Override
  public List<com.outpost.payment.refund.Refund> findRefundsByOriginalReference(
      String originalReference) {
    Map<Long, List<RefundItem>> itemsByRefund = new HashMap<>();
    for (RefundItem item : mapper.findRefundItemsByOriginalReference(originalReference)) {
      itemsByRefund.computeIfAbsent(item.refundId(), refundId -> new ArrayList<>()).add(item);
    }
    return mapper.findRefundsByOriginalReference(originalReference).stream()
        .map(row -> toRefund(row, itemsByRefund.getOrDefault(row.refundId(), List.of())))
        .toList();
  }

  @Override
  public void updateRefundPspRefundReference(String refundReference, String pspRefundReference) {
    mapper.updateRefundPspRefundReference(refundReference, pspRefundReference);
  }

  @Override
  public void updateRefundItemRefundFailed(String refundReference) {
    mapper.updateRefundItemRefundFailed(refundReference);
  }

  private static List<Long> claimedLineIds(com.outpost.payment.refund.Refund refund) {
    return refund.items().stream()
        .map(item -> item.orderItem().getOrderItemId().orElseThrow())
        .sorted()
        .toList();
  }

  private com.outpost.payment.refund.Refund toFoundRefund(Refund row) {
    return toRefund(
        row, mapper.findRefundItems(Objects.requireNonNull(row.refundId(), "refundId")));
  }

  private static com.outpost.payment.refund.Refund toRefund(Refund row, List<RefundItem> items) {
    Currency currency =
        Currencies.fromCurrencyCode(row.currency())
            .orElseThrow(() -> new IllegalStateException("Stored currency is not supported"));
    return new com.outpost.payment.refund.Refund(
        row.refundId(),
        row.refundReference(),
        row.orderId(),
        row.originalReference(),
        row.merchantReference(),
        row.idempotencyKey(),
        row.pspRefundReference(),
        items.stream()
            .map(
                item ->
                    new com.outpost.payment.refund.RefundItem(
                        item.refundItemId(),
                        MyBatisOrderRepository.toOrderItem(item.orderItem(), currency),
                        item.refundFailed()))
            .toList(),
        row.createdAt());
  }
}
