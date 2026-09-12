package com.outpost.gateway.paymentmethod.api;

import com.outpost.gateway.paymentmethod.repository.PaymentMethodRepository;
import com.outpost.gateway.security.GatewayPrincipal;
import com.outpost.gateway.security.MerchantAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lists the PSPs enabled for the authenticated caller. */
@RestController
@RequestMapping("/v1/payment-methods")
public final class PaymentMethodController {
  private final PaymentMethodRepository repository;

  /** Creates a controller backed by the payment method repository. */
  public PaymentMethodController(PaymentMethodRepository repository) {
    this.repository = repository;
  }

  /** Returns the enabled payment methods for the authenticated caller. */
  @GetMapping
  public PaymentMethodsResponse list(HttpServletRequest httpRequest) {
    GatewayPrincipal principal =
        Objects.requireNonNull(
            (GatewayPrincipal)
                httpRequest.getAttribute(MerchantAuthenticationFilter.PRINCIPAL_ATTRIBUTE),
            "principal");
    return PaymentMethodsResponse.from(repository.findEnabled(principal.accountId()));
  }
}
