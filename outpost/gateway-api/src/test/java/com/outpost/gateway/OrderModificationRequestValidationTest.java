package com.outpost.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.outpost.gateway.api.GatewayErrorAdvice;
import com.outpost.gateway.order.api.OrderModificationController;
import com.outpost.gateway.order.api.OrderModificationRequest;
import com.outpost.gateway.order.service.OrderModificationService;
import com.outpost.gateway.security.GatewayPrincipal;
import com.outpost.gateway.security.GatewayPrincipalArgumentResolver;
import com.outpost.gateway.security.MerchantAuthenticationFilter;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** A malformed modification request is refused at the boundary with its declared code. */
class OrderModificationRequestValidationTest {
  private static final String VALID =
      """
      {"order_reference":"order-1","idempotency_key":"k-2","merchant_reference":"m-2",
       "type":"REFUND"}
      """;

  private final OrderServiceFakes fakes = new OrderServiceFakes();
  private final MockMvc mockMvc =
      MockMvcBuilders.standaloneSetup(
              new OrderModificationController(
                  new OrderModificationService(
                      fakes.repository,
                      fakes.accounts,
                      fakes.psp,
                      refund -> {
                        throw new UnsupportedOperationException();
                      })))
          .setControllerAdvice(new GatewayErrorAdvice())
          .setCustomArgumentResolvers(new GatewayPrincipalArgumentResolver())
          .build();

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidRequests")
  void refusesTheRequestWithItsDeclaredCodeBeforeAnyOrderIsRead(
      String scenario, String body, String code) throws Exception {
    fakes.repository.failure = new IllegalStateException("the order store was read");

    mockMvc
        .perform(modification(body))
        .andExpect(status().isBadRequest())
        .andExpect(content().json("{\"code\":\"%s\"}".formatted(code)));
  }

  static Stream<Arguments> invalidRequests() {
    return Stream.of(
        Arguments.of(
            "blank order_reference",
            VALID.replace("\"order_reference\":\"order-1\"", "\"order_reference\":\" \""),
            OrderModificationRequest.INVALID_ORDER_REFERENCE),
        Arguments.of(
            "over-length idempotency_key",
            VALID.replace(
                "\"idempotency_key\":\"k-2\"", "\"idempotency_key\":\"" + "k".repeat(129) + "\""),
            OrderModificationRequest.INVALID_IDEMPOTENCY_KEY),
        Arguments.of(
            "missing merchant_reference",
            VALID.replace(",\"merchant_reference\":\"m-2\"", ""),
            OrderModificationRequest.INVALID_MERCHANT_REFERENCE),
        Arguments.of(
            "blank type",
            VALID.replace("\"type\":\"REFUND\"", "\"type\":\"\""),
            OrderModificationRequest.INVALID_TYPE));
  }

  @Test
  void refusesNullBodyAsInvalidRequest() throws Exception {
    mockMvc
        .perform(modification("null"))
        .andExpect(status().isBadRequest())
        .andExpect(content().json("{\"code\":\"INVALID_REQUEST\"}"));
  }

  @Test
  void handsValidRequestToTheService() throws Exception {
    mockMvc
        .perform(modification(VALID))
        .andExpect(status().isNotFound())
        .andExpect(content().json("{\"code\":\"ORDER_NOT_FOUND\"}"));

    assertThat(fakes.repository.orders).isEmpty();
  }

  private static MockHttpServletRequestBuilder modification(String body) {
    return post("/v1/order/modification")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .requestAttr(
            MerchantAuthenticationFilter.PRINCIPAL_ATTRIBUTE,
            GatewayPrincipal.merchant(OrderServiceFakes.MERCHANT_ACCOUNT_ID));
  }
}
