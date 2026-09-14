package com.outpost.pspsimulator;

import com.outpost.pspsimulator.order.OrderService;
import com.outpost.pspsimulator.psp.PspAccount;
import com.outpost.pspsimulator.psp.PspAccounts;
import com.outpost.pspsimulator.refund.RefundService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The simulator's PSP API, one set of routes per PSP code. */
@RestController
@RequestMapping("/v1/{pspCode}")
public class PspApiController {

  private static final String API_KEY_HEADER = "X-Outpost-Api-Key";

  private final PspAccounts pspAccounts;
  private final OrderService orderService;
  private final RefundService refundService;

  /** Creates the API controller with its PSP registry and payment operations. */
  public PspApiController(
      PspAccounts pspAccounts, OrderService orderService, RefundService refundService) {
    this.pspAccounts = pspAccounts;
    this.orderService = orderService;
    this.refundService = refundService;
  }

  /** Creates an order, or returns the existing one when the payment reference repeats. */
  @PostMapping("/order")
  public ResponseEntity<CreateOrderResponse> createOrder(
      @PathVariable String pspCode,
      @RequestBody CreateOrderRequest request,
      @RequestHeader(value = API_KEY_HEADER, required = false) @Nullable String apiKey) {
    requirePspAccount(pspCode, apiKey);
    OrderService.CreateOrderResult result = orderService.createOrder(pspCode, request.toCommand());
    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status)
        .body(new CreateOrderResponse(result.order().pspReference(), result.paymentUrl()));
  }

  /** Submits a card number against an order; the outcome arrives later by webhook. */
  @PostMapping("/payment")
  public ResponseEntity<Void> pay(
      @PathVariable String pspCode,
      @RequestBody PayRequest request,
      @RequestHeader(value = API_KEY_HEADER, required = false) @Nullable String apiKey) {
    requirePspAccount(pspCode, apiKey);
    orderService.pay(pspCode, request.toCommand());
    return ResponseEntity.accepted().build();
  }

  /**
   * Refunds a captured order in full, echoing the caller's refund reference on the REFUND webhook.
   */
  @PostMapping("/refund")
  public ResponseEntity<RefundResponse> refund(
      @PathVariable String pspCode,
      @RequestBody RefundRequest request,
      @RequestHeader(value = API_KEY_HEADER, required = false) @Nullable String apiKey) {
    requirePspAccount(pspCode, apiKey);
    RefundService.RefundResult result = refundService.refund(pspCode, request.toCommand());
    return ResponseEntity.ok(new RefundResponse(result.pspRefundReference(), result.accepted()));
  }

  private PspAccount requirePspAccount(String pspCode, @Nullable String apiKey) {
    PspAccount account =
        pspAccounts
            .findByCode(pspCode)
            .orElseThrow(() -> new NotFoundException("no PSP with code: " + pspCode));
    if (!matches(account.apiKey(), apiKey)) {
      throw new UnauthorizedException("invalid API key for PSP: " + pspCode);
    }
    return account;
  }

  private static boolean matches(String expected, @Nullable String presented) {
    if (presented == null) {
      return false;
    }
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
  }
}
