package com.outpost.gateway.paymentmethod.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.outpost.gateway.paymentmethod.repository.PaymentMethodRepository;
import java.util.List;

/** HTTP response listing a merchant's enabled payment methods. */
public record PaymentMethodsResponse(
    @JsonProperty("payment_methods") List<PaymentMethod> paymentMethods) {
  /** Copies the payment method list so the response cannot be mutated through its input. */
  public PaymentMethodsResponse {
    paymentMethods = List.copyOf(paymentMethods);
  }

  /** Converts the application read model to the HTTP contract. */
  public static PaymentMethodsResponse from(List<PaymentMethodRepository.PaymentMethod> methods) {
    return new PaymentMethodsResponse(methods.stream().map(PaymentMethod::from).toList());
  }

  /** One enabled PSP in the HTTP response. */
  public record PaymentMethod(
      @JsonProperty("psp_code") String pspCode, @JsonProperty("name") String name) {
    private static PaymentMethod from(PaymentMethodRepository.PaymentMethod method) {
      return new PaymentMethod(method.pspCode(), method.name());
    }
  }
}
