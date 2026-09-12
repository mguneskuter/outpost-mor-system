package com.outpost.worker.accounting;

import com.outpost.accounting.queue.AccountingRequest;
import com.outpost.accounting.queue.AccountingRequestLine;
import com.outpost.accounting.queue.AccountingRequestResults;
import com.outpost.integration.psp.service.CancelRequest;
import com.outpost.integration.psp.service.CancelResult;
import com.outpost.integration.psp.service.PspClient;
import com.outpost.integration.psp.service.RefundRequest;
import com.outpost.integration.psp.service.RefundResult;
import com.outpost.integration.psp.service.ResultCode;
import com.outpost.payment.RefundItem;
import com.outpost.payment.common.Amount;
import com.outpost.payment.order.Order;
import com.outpost.payment.order.OrderItem;
import com.outpost.payment.repository.PaymentOrderRepository;
import com.outpost.payment.repository.PaymentOrderRepository.PspRouting;
import com.outpost.payment.repository.RefundItemRepository;
import com.outpost.worker.accounting.RefundLineComputation.LineRefund;
import com.outpost.worker.accounting.RefundLineComputation.RefundLineRejectedException;
import com.outpost.worker.accounting.client.LedgerPaymentClient;
import com.outpost.worker.accounting.client.LedgerPaymentClientException;
import com.outpost.worker.accounting.repository.LedgerTransactionRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Carries out a merchant's queued refund request: cancels an authorised, uncaptured payment at the
 * PSP, or reserves, refunds, and books a captured one.
 */
public final class MerchantRefundWorkflow {
  private final PaymentOrderRepository orders;
  private final RefundItemRepository refundItems;
  private final LedgerTransactionRepository transactions;
  private final LedgerPaymentClient ledger;
  private final PspClient psp;

  /** Creates the workflow over its repository, Ledger, and PSP collaborators. */
  public MerchantRefundWorkflow(
      PaymentOrderRepository orders,
      RefundItemRepository refundItems,
      LedgerTransactionRepository transactions,
      LedgerPaymentClient ledger,
      PspClient psp) {
    this.orders = orders;
    this.refundItems = refundItems;
    this.transactions = transactions;
    this.ledger = ledger;
    this.psp = psp;
  }

  AccountingRequestResults apply(AccountingRequest request) {
    String paymentReference = request.getOriginalReference();
    PspRouting routing =
        orders
            .findPspRoutingByPaymentReference(paymentReference)
            .orElseThrow(() -> noRouting(paymentReference));
    String pspReference =
        Objects.requireNonNull(
            routing.pspReference(), "payment must be PSP-confirmed before it can be refunded");
    if (!transactions.isCaptured(paymentReference)) {
      return cancel(routing.pspCode(), pspReference);
    }
    return refund(request, paymentReference, routing.pspCode(), pspReference);
  }

  private AccountingRequestResults cancel(String pspCode, String pspReference) {
    CancelResult result = psp.cancel(new CancelRequest(pspCode, pspReference));
    return result.resultCode() == ResultCode.ACCEPTED
        ? AccountingRequestResults.SUCCESS
        : AccountingRequestResults.FAILED;
  }

  private AccountingRequestResults refund(
      AccountingRequest request, String paymentReference, String pspCode, String pspReference) {
    Order order =
        orders
            .findByPaymentReference(paymentReference)
            .orElseThrow(() -> noOrder(paymentReference));
    List<LineRefund> planned;
    try {
      planned = plan(order, request);
    } catch (RefundLineRejectedException exception) {
      return AccountingRequestResults.FAILED;
    }
    long totalNet = planned.stream().mapToLong(LineRefund::net).sum();
    long totalTax = planned.stream().mapToLong(LineRefund::tax).sum();
    String currencyCode = order.getNetAmount().currency().getCurrencyCode();

    try {
      ledger.reserveRefund(
          paymentReference, request.getReference(), totalNet, totalTax, currencyCode);
    } catch (LedgerPaymentClientException exception) {
      return AccountingRequestResults.FAILED;
    }

    long refundTransactionId =
        transactions
            .findTransactionId(request.getReference())
            .orElseThrow(() -> noRefundTransaction(request.getReference()));
    Amount currencyUnit = order.getNetAmount();
    for (LineRefund line : planned) {
      refundItems.insert(
          new RefundItem(
              refundTransactionId,
              line.orderItemId(),
              new Amount(currencyUnit.currency(), line.net()),
              new Amount(currencyUnit.currency(), line.tax())));
    }

    RefundResult pspResult =
        psp.refund(
            new RefundRequest(
                pspCode,
                pspReference,
                request.getReference(),
                new Amount(currencyUnit.currency(), totalNet + totalTax)));
    return switch (pspResult.resultCode()) {
      case ACCEPTED ->
          recordRefundOutcome(
              paymentReference, request, "REFUND_ACCEPTED", AccountingRequestResults.SUCCESS);
      case REJECTED ->
          recordRefundOutcome(
              paymentReference, request, "REFUND_FAILED", AccountingRequestResults.FAILED);
      // An unknown PSP outcome must not release the Ledger reservation: the PSP may have
      // actually processed the refund, so reporting REFUND_FAILED here would let the amount
      // be refunded again while reconciliation is still pending.
      case UNKNOWN -> AccountingRequestResults.FAILED;
    };
  }

  private AccountingRequestResults recordRefundOutcome(
      String paymentReference,
      AccountingRequest request,
      String event,
      AccountingRequestResults onRecorded) {
    try {
      ledger.recordEvent(paymentReference, request.getReference(), event);
    } catch (LedgerPaymentClientException exception) {
      return AccountingRequestResults.FAILED;
    }
    return onRecorded;
  }

  private List<LineRefund> plan(Order order, AccountingRequest request) {
    List<AccountingRequestLine> lines = request.getLines();
    List<OrderItem> targets;
    Map<String, @Nullable Long> requestedAmounts;
    if (lines.isEmpty()) {
      targets = order.getItems();
      requestedAmounts = Map.of();
    } else {
      Map<String, OrderItem> itemsByReference =
          order.getItems().stream()
              .collect(Collectors.toMap(OrderItem::getOrderLineReference, Function.identity()));
      targets =
          lines.stream()
              .map(line -> requireItem(itemsByReference, line.orderLineReference()))
              .toList();
      Map<String, @Nullable Long> amounts = new HashMap<>();
      for (AccountingRequestLine line : lines) {
        amounts.put(line.orderLineReference(), line.amount());
      }
      requestedAmounts = amounts;
    }
    List<LineRefund> planned = new ArrayList<>();
    for (OrderItem item : targets) {
      LedgerTransactionRepository.RefundedTotal alreadyActive =
          transactions.activeRefundedTotal(item.getOrderItemId(), request.getReference());
      planned.add(
          RefundLineComputation.compute(
              item, alreadyActive, requestedAmounts.get(item.getOrderLineReference())));
    }
    return planned;
  }

  private static OrderItem requireItem(
      Map<String, OrderItem> itemsByReference, String orderLineReference) {
    OrderItem item = itemsByReference.get(orderLineReference);
    if (item == null) {
      throw new IllegalStateException("Unknown order line reference: " + orderLineReference);
    }
    return item;
  }

  private static IllegalStateException noRouting(String paymentReference) {
    return new IllegalStateException("No PSP routing for payment: " + paymentReference);
  }

  private static IllegalStateException noOrder(String paymentReference) {
    return new IllegalStateException("No order for payment: " + paymentReference);
  }

  private static IllegalStateException noRefundTransaction(String reference) {
    return new IllegalStateException("Reserved refund transaction not found: " + reference);
  }
}
