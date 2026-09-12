package com.outpost.gateway.order.api;

import com.outpost.gateway.order.service.OrderModificationException;
import com.outpost.gateway.order.service.OrderModificationService;
import com.outpost.gateway.security.GatewayPrincipal;
import com.outpost.gateway.security.MerchantAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
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

  /** Submits a modification request for an order owned by the authenticated merchant. */
  @PostMapping
  public ResponseEntity<OrderModificationResponse> request(
      @RequestBody OrderModificationRequest request, HttpServletRequest httpRequest) {
    GatewayPrincipal principal =
        (GatewayPrincipal)
            httpRequest.getAttribute(MerchantAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
    if (principal == null || principal.type() != GatewayPrincipal.Type.MERCHANT) {
      throw new OrderModificationException(HttpStatus.FORBIDDEN.value(), "MERCHANT_REQUIRED");
    }
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            OrderModificationResponse.from(
                service.request(principal.accountId(), request.toCommand())));
  }

  @ExceptionHandler(OrderModificationException.class)
  ResponseEntity<ErrorResponse> controlled(OrderModificationException exception) {
    return ResponseEntity.status(exception.status()).body(new ErrorResponse(exception.code()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ErrorResponse> malformed(HttpMessageNotReadableException ignored) {
    return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REQUEST"));
  }

  @ExceptionHandler(RuntimeException.class)
  ResponseEntity<ErrorResponse> unexpected(@Nullable RuntimeException ignored) {
    return ResponseEntity.internalServerError().body(new ErrorResponse("INTERNAL_ERROR"));
  }

  record ErrorResponse(String code) {}
}
