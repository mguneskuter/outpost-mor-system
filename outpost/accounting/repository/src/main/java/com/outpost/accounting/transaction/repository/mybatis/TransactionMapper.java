package com.outpost.accounting.transaction.repository.mybatis;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

/**
 * MyBatis statements for transactions, their details, and their events. Package-private, like the
 * stored forms it returns: a MyBatis proxy of a public interface is defined in another module and
 * cannot reach package-private result types.
 */
interface TransactionMapper {
  @Nullable Transaction insertTransaction(Transaction transaction);

  void insertPaymentDetail(PaymentDetail paymentDetail);

  @Nullable PaymentDetail findPaymentDetailByReference(@Param("reference") String reference);

  @Nullable PaymentDetail findPaymentDetailByReferenceForUpdate(
      @Param("reference") String reference);

  @Nullable Transaction findTransactionById(@Param("transactionId") long transactionId);

  List<Transaction> findChildTransactions(
      @Param("parentTransactionId") long parentTransactionId,
      @Param("transactionType") String transactionType);

  List<TransactionEvent> findTransactionEvents(@Param("transactionId") long transactionId);

  @Nullable TransactionEvent insertTransactionEvent(
      @Param("transactionId") long transactionId,
      @Param("transactionEventType") String transactionEventType);

  void insertRefundDetail(RefundDetail refundDetail);

  @Nullable RefundDetail findRefundDetailByReference(@Param("reference") String reference);

  List<RefundDetail> findRefundDetails(@Param("parentTransactionId") long parentTransactionId);
}
