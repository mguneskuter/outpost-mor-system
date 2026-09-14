package com.outpost.gateway.order.api;

import com.outpost.gateway.order.service.CreateOrderErrorCodes;
import com.outpost.gateway.order.service.CreateOrderException;
import com.outpost.gateway.order.service.OrderModificationErrorCodes;
import com.outpost.gateway.order.service.OrderModificationException;
import com.outpost.gateway.order.service.OrderModificationService;
import com.outpost.gateway.order.service.OrderService;
import com.outpost.gateway.security.GatewayPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Creates merchant payment orders and modifies them. */
@RestController
@RequestMapping("/v1/order")
public final class OrderController {
  private final OrderService orderService;
  private final OrderModificationService orderModificationService;

  /** Creates a controller backed by the order services. */
  public OrderController(
      OrderService orderService, OrderModificationService orderModificationService) {
    this.orderService = orderService;
    this.orderModificationService = orderModificationService;
  }

  /** Creates an order for the authenticated merchant. */
  @PostMapping
  public ResponseEntity<CreateOrderResponse> create(
      GatewayPrincipal principal, @Valid @RequestBody CreateOrderRequest request) {
    if (principal.type() != GatewayPrincipal.Type.MERCHANT) {
      throw new CreateOrderException(CreateOrderErrorCodes.MERCHANT_REQUIRED);
    }
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            CreateOrderResponse.from(
                orderService.create(principal.accountId(), request.toCommand())));
  }

  /** Refunds an order owned by the authenticated merchant. */
  @PostMapping("/modification")
  public ResponseEntity<OrderModificationResponse> modify(
      GatewayPrincipal principal, @Valid @RequestBody OrderModificationRequest request) {
    if (principal.type() != GatewayPrincipal.Type.MERCHANT) {
      throw new OrderModificationException(OrderModificationErrorCodes.MERCHANT_REQUIRED);
    }
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            OrderModificationResponse.from(
                orderModificationService.modify(principal.accountId(), request.toCommand())));
  }
}
