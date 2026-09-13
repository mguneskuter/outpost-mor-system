package com.outpost.gateway.order.api;

import com.outpost.gateway.order.service.OrderCreationException;
import com.outpost.gateway.order.service.OrderService;
import com.outpost.gateway.security.GatewayPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Creates merchant payment orders. */
@RestController
@RequestMapping("/v1/order")
public final class OrderController {
  private final OrderService service;

  /** Creates a controller backed by the order service. */
  public OrderController(OrderService service) {
    this.service = service;
  }

  /** Creates an order for the authenticated merchant. */
  @PostMapping
  public ResponseEntity<CreateOrderResponse> create(
      GatewayPrincipal principal, @Valid @RequestBody CreateOrderRequest request) {
    if (principal.type() != GatewayPrincipal.Type.MERCHANT) {
      throw new OrderCreationException(HttpStatus.FORBIDDEN.value(), "MERCHANT_REQUIRED");
    }
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(CreateOrderResponse.from(service.create(principal.accountId(), request.toCommand())));
  }
}
