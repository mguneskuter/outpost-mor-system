package com.outpost.gateway.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.outpost.gateway.api.GatewayErrorAdvice;
import com.outpost.gateway.order.service.OrderModificationService;
import com.outpost.gateway.order.service.OrderServiceFakes;
import com.outpost.gateway.security.GatewayPrincipal;
import com.outpost.gateway.security.GatewayPrincipalArgumentResolver;
import com.outpost.gateway.security.MerchantAuthenticationFilter;
import java.util.Collections;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** A malformed order request is refused at the boundary with the code its constraint declares. */
class CreateOrderRequestValidationTest {
  private static final String SHOPPER =
      "{\"full_name\":\"Shopper\",\"email\":\"s@example.test\",\"country\":\"DE\"}";
  private static final String LINES =
      "[{\"merchant_line_reference\":\"l-1\",\"amount\":100,\"currency\":\"EUR\","
          + "\"type\":\"DIGITAL_GOODS\"}]";
  private static final String ORDER_DETAILS =
      "{\"order_lines\":" + LINES + ",\"total_amount\":100,\"currency\":\"EUR\"}";
  private static final String VALID =
      "{\"merchant_reference\":\"m-1\",\"idempotency_key\":\"k-1\",\"psp_code\":\"DEMO_PSP\","
          + "\"shopper_details\":"
          + SHOPPER
          + ",\"order_details\":"
          + ORDER_DETAILS
          + "}";

  private final OrderServiceFakes fakes = new OrderServiceFakes();
  private final MockMvc mockMvc =
      MockMvcBuilders.standaloneSetup(
              new OrderController(
                  fakes.service,
                  new OrderModificationService(
                      fakes.repository,
                      fakes.psp,
                      refund -> {
                        throw new UnsupportedOperationException();
                      })))
          .setControllerAdvice(new GatewayErrorAdvice())
          .setCustomArgumentResolvers(new GatewayPrincipalArgumentResolver())
          .build();

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidRequests")
  void refusesTheRequestWithItsDeclaredCodeBeforeAnyOrderIsStored(
      String scenario, String body, String code) throws Exception {
    mockMvc
        .perform(order(body))
        .andExpect(status().isBadRequest())
        .andExpect(content().json("{\"code\":\"%s\"}".formatted(code)));

    assertThat(fakes.repository.orders).isEmpty();
    assertThat(fakes.pspRequests).isEmpty();
  }

  static Stream<Arguments> invalidRequests() {
    return Stream.of(
        Arguments.of(
            "blank merchant_reference",
            VALID.replace("\"merchant_reference\":\"m-1\"", "\"merchant_reference\":\" \""),
            CreateOrderRequest.INVALID_MERCHANT_REFERENCE),
        Arguments.of(
            "over-length idempotency_key",
            VALID.replace(
                "\"idempotency_key\":\"k-1\"", "\"idempotency_key\":\"" + "k".repeat(129) + "\""),
            CreateOrderRequest.INVALID_IDEMPOTENCY_KEY),
        Arguments.of(
            "missing psp_code",
            VALID.replace("\"psp_code\":\"DEMO_PSP\",", ""),
            CreateOrderRequest.INVALID_PSP_CODE),
        Arguments.of(
            "missing shopper_details",
            VALID.replace("\"shopper_details\":" + SHOPPER + ",", ""),
            CreateOrderRequest.INVALID_SHOPPER_DETAILS),
        Arguments.of(
            "blank shopper email",
            VALID.replace("\"email\":\"s@example.test\"", "\"email\":\"\""),
            CreateOrderRequest.ShopperDetails.INVALID_SHOPPER_DETAILS_EMAIL),
        Arguments.of(
            "over-length shopper full_name",
            VALID.replace("\"full_name\":\"Shopper\"", "\"full_name\":\"" + "n".repeat(255) + "\""),
            CreateOrderRequest.ShopperDetails.INVALID_SHOPPER_DETAILS_FULL_NAME),
        Arguments.of(
            "missing shopper country",
            VALID.replace(",\"country\":\"DE\"", ""),
            CreateOrderRequest.ShopperDetails.INVALID_SHOPPER_DETAILS_COUNTRY),
        Arguments.of(
            "missing order_details",
            VALID.replace(",\"order_details\":" + ORDER_DETAILS, ""),
            CreateOrderRequest.INVALID_ORDER_DETAILS),
        Arguments.of(
            "empty order_lines",
            VALID.replace(LINES, "[]"),
            CreateOrderRequest.OrderDetails.INVALID_ORDER_LINES),
        Arguments.of(
            "over-long order_lines",
            VALID.replace(LINES, "[" + String.join(",", Collections.nCopies(101, line())) + "]"),
            CreateOrderRequest.OrderDetails.INVALID_ORDER_LINES),
        Arguments.of(
            "null order line",
            VALID.replace(LINES, "[null]"),
            CreateOrderRequest.OrderDetails.INVALID_ORDER_LINE),
        Arguments.of(
            "non-positive total_amount",
            VALID.replace("\"total_amount\":100", "\"total_amount\":0"),
            CreateOrderRequest.OrderDetails.INVALID_ORDER_DETAILS_TOTAL_AMOUNT),
        Arguments.of(
            "blank order currency",
            VALID.replace(
                "\"total_amount\":100,\"currency\":\"EUR\"",
                "\"total_amount\":100,\"currency\":\"\""),
            CreateOrderRequest.OrderDetails.INVALID_ORDER_DETAILS_CURRENCY),
        Arguments.of(
            "blank merchant_line_reference",
            VALID.replace(
                "\"merchant_line_reference\":\"l-1\"", "\"merchant_line_reference\":\"\""),
            CreateOrderRequest.OrderLine.INVALID_MERCHANT_LINE_REFERENCE),
        Arguments.of(
            "non-positive line amount",
            VALID.replace("\"amount\":100", "\"amount\":-1"),
            CreateOrderRequest.OrderLine.INVALID_ORDER_LINES_AMOUNT),
        Arguments.of(
            "missing line currency",
            VALID.replace("\"amount\":100,\"currency\":\"EUR\"", "\"amount\":100"),
            CreateOrderRequest.OrderLine.INVALID_ORDER_LINES_CURRENCY),
        Arguments.of(
            "missing line type",
            VALID.replace(",\"type\":\"DIGITAL_GOODS\"", ""),
            CreateOrderRequest.OrderLine.INVALID_ORDER_LINES_TYPE));
  }

  @Test
  void refusesNullBodyAsInvalidRequest() throws Exception {
    mockMvc
        .perform(order("null"))
        .andExpect(status().isBadRequest())
        .andExpect(content().json("{\"code\":\"INVALID_REQUEST\"}"));

    assertThat(fakes.repository.orders).isEmpty();
  }

  @Test
  void acceptsValidRequestAndCreatesTheOrder() throws Exception {
    mockMvc.perform(order(VALID)).andExpect(status().isCreated());

    assertThat(fakes.repository.orders).hasSize(1);
    assertThat(fakes.pspRequests).hasSize(1);
  }

  private static String line() {
    return "{\"merchant_line_reference\":\"l\",\"amount\":1,\"currency\":\"EUR\","
        + "\"type\":\"DIGITAL_GOODS\"}";
  }

  private static MockHttpServletRequestBuilder order(String body) {
    return post("/v1/order")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .requestAttr(
            MerchantAuthenticationFilter.PRINCIPAL_ATTRIBUTE,
            GatewayPrincipal.merchant(OrderServiceFakes.MERCHANT_ACCOUNT_ID));
  }
}
