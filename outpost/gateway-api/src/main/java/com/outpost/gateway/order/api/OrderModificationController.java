package com.outpost.gateway.order.api;

import com.outpost.gateway.order.service.ModifyOrderException;
import com.outpost.gateway.order.service.OrderModificationService;
import com.outpost.gateway.security.GatewayPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Modifies a merchant's existing payment order. */
@RestController
@RequestMapping("/v1/order/modification")
public final class OrderModificationController {
  private final OrderModificationService service;

  /** Creates a controller backed by the order modification service. */
  public OrderModificationController(OrderModificationService service) {
    this.service = service;
  }

  /** Refunds an order owned by the authenticated merchant. */
  @PostMapping
  public ResponseEntity<OrderModificationResponse> request(
      GatewayPrincipal principal, @Valid @RequestBody OrderModificationRequest request) {
    if (principal.type() != GatewayPrincipal.Type.MERCHANT) {
      throw new ModifyOrderException(HttpStatus.FORBIDDEN.value(), "MERCHANT_REQUIRED");
    }
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            OrderModificationResponse.from(
                service.request(principal.accountId(), request.toCommand())));
  }
}
