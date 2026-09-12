package com.outpost.gateway.order.service;

import com.outpost.accounting.queue.AccountingRequest;
import com.outpost.accounting.queue.AccountingRequestLine;
import com.outpost.accounting.queue.AccountingRequestQueue;
import com.outpost.accounting.queue.AccountingRequestTypes;
import com.outpost.accounting.queue.SubmitAccountingRequestCommand;
import com.outpost.gateway.order.repository.OrderRepository;
import com.outpost.gateway.order.repository.OrderRepository.Line;
import com.outpost.gateway.order.repository.OrderRepository.PersistedOrder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

/** Validates and submits merchant order modification requests. */
public final class OrderModificationService {
  private static final String REFUND_TYPE = "REFUND";
  private final OrderRepository orders;
  private final AccountingRequestQueue queue;

  /** Creates a service backed by order lookup and the accounting request queue. */
  public OrderModificationService(OrderRepository orders, AccountingRequestQueue queue) {
    this.orders = orders;
    this.queue = queue;
  }

  /** Submits one modification request for an order owned by the caller. */
  public OrderModificationResult request(long merchantAccountId, OrderModificationCommand command) {
    String type = required(command.type(), "type");
    if (!REFUND_TYPE.equals(type)) {
      throw failure(HttpStatus.BAD_REQUEST.value(), "UNSUPPORTED_MODIFICATION_TYPE");
    }
    String orderReference = required(command.orderReference(), "order_reference");
    String idempotencyKey = required(command.idempotencyKey(), "idempotency_key");
    String merchantReference = required(command.merchantReference(), "merchant_reference");

    PersistedOrder order = orders.findByReference(merchantAccountId, orderReference);
    if (order == null) {
      throw failure(HttpStatus.NOT_FOUND.value(), "ORDER_NOT_FOUND");
    }
    List<AccountingRequestLine> lines = resolveLines(order, command.refundLines());

    String refundReference = "refund-" + UUID.randomUUID();
    AccountingRequest stored =
        queue.submit(
            new SubmitAccountingRequestCommand(
                AccountingRequestTypes.REFUND_REQUEST,
                refundReference,
                order.paymentReference(),
                merchantAccountId,
                null,
                idempotencyKey,
                merchantReference,
                null,
                null,
                null,
                null,
                lines));
    return new OrderModificationResult(stored.getReference(), stored.getStatus().name());
  }

  private static List<AccountingRequestLine> resolveLines(
      PersistedOrder order, @Nullable List<OrderModificationCommand.RefundLineCommand> requested) {
    if (requested == null || requested.isEmpty()) {
      return List.of();
    }
    Map<String, Line> byOrderLineReference =
        order.lines().stream()
            .collect(Collectors.toMap(Line::orderLineReference, Function.identity()));
    Map<String, Line> byMerchantLineReference =
        order.lines().stream()
            .collect(Collectors.toMap(Line::merchantLineReference, Function.identity()));
    List<AccountingRequestLine> resolved = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (OrderModificationCommand.RefundLineCommand line : requested) {
      if (line == null) {
        throw failure(HttpStatus.BAD_REQUEST.value(), "INVALID_REFUND_LINE");
      }
      boolean hasOrderLineReference = isPresent(line.orderLineReference());
      boolean hasMerchantLineReference = isPresent(line.merchantLineReference());
      if (hasOrderLineReference == hasMerchantLineReference) {
        throw failure(HttpStatus.BAD_REQUEST.value(), "AMBIGUOUS_LINE_REFERENCE");
      }
      Line matched =
          hasOrderLineReference
              ? byOrderLineReference.get(line.orderLineReference())
              : byMerchantLineReference.get(line.merchantLineReference());
      if (matched == null) {
        throw failure(HttpStatus.BAD_REQUEST.value(), "UNKNOWN_ORDER_LINE");
      }
      Long amount = line.amount();
      if (amount != null && amount <= 0) {
        throw failure(HttpStatus.BAD_REQUEST.value(), "LINE_AMOUNT_MUST_BE_POSITIVE");
      }
      if (!seen.add(matched.orderLineReference())) {
        throw failure(HttpStatus.BAD_REQUEST.value(), "DUPLICATE_LINE_REFERENCE");
      }
      resolved.add(new AccountingRequestLine(matched.orderLineReference(), amount));
    }
    return resolved;
  }

  private static boolean isPresent(@Nullable String value) {
    return value != null && !value.isBlank();
  }

  private static String required(@Nullable String value, String field) {
    if (value == null || value.isBlank()) {
      throw failure(
          HttpStatus.BAD_REQUEST.value(),
          "INVALID_" + field.toUpperCase(Locale.ROOT).replace('.', '_'));
    }
    return value;
  }

  private static OrderModificationException failure(int status, String code) {
    return new OrderModificationException(status, code);
  }
}
