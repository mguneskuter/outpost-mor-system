package com.outpost.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.outpost.account.Account;
import com.outpost.account.configuration.repository.MerchantPspRepository;
import com.outpost.accounting.report.BalanceReport;
import com.outpost.accounting.report.ReportPeriod;
import com.outpost.gateway.api.ErrorResponse;
import com.outpost.gateway.api.GatewayErrorAdvice;
import com.outpost.gateway.order.api.OrderController;
import com.outpost.gateway.order.api.OrderModificationController;
import com.outpost.gateway.order.service.ModifyOrderException;
import com.outpost.gateway.order.service.OrderCreationException;
import com.outpost.gateway.order.service.OrderModificationService;
import com.outpost.gateway.psp.api.PspController;
import com.outpost.gateway.report.api.ReportController;
import com.outpost.gateway.report.client.LedgerReportClient;
import com.outpost.gateway.report.service.GeneratedReports;
import com.outpost.gateway.report.service.ReportService;
import com.outpost.gateway.security.GatewayPrincipal;
import com.outpost.gateway.security.GatewayPrincipalArgumentResolver;
import com.outpost.gateway.security.MerchantAuthenticationFilter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

/**
 * Every controller failure, controlled or not, answers the one Gateway error body. The services are
 * real; the boundary they call fails with the exception each case chooses.
 */
class GatewayErrorContractTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String PERIOD = "?from=2026-09-01&to=2026-09-30";
  private static final String VALID_ORDER =
      """
      {"merchant_reference":"m-1","idempotency_key":"k-1","psp_code":"PSP",
       "shopper_details":{"full_name":"Shopper","email":"s@example.test","country":"DE"},
       "order_details":{"order_lines":[{"merchant_line_reference":"l-1","amount":100,
       "currency":"EUR","type":"DIGITAL_GOODS"}],"total_amount":100,"currency":"EUR"}}
      """;
  private static final String VALID_MODIFICATION =
      """
      {"order_reference":"order-1","idempotency_key":"k-2","merchant_reference":"m-2",
       "type":"REFUND"}
      """;

  private final OrderServiceFakes orderFakes = new OrderServiceFakes();
  private final MockMvc mockMvc =
      MockMvcBuilders.standaloneSetup(
              new OrderController(orderFakes.service),
              new OrderModificationController(
                  new OrderModificationService(
                      orderFakes.repository,
                      orderFakes.psp,
                      refund -> {
                        throw new UnsupportedOperationException();
                      })),
              new ReportController(
                  new ReportService(
                      new FailingLedger(), new FailingMerchants(), new GeneratedReports(1))),
              new PspController(new FailingMerchantPsps()))
          .setControllerAdvice(new GatewayErrorAdvice())
          .setCustomArgumentResolvers(new GatewayPrincipalArgumentResolver())
          .build();

  @ParameterizedTest(name = "{0}")
  @MethodSource("controlledFailures")
  void answersControlledFailureWithItsStatusAndCode(
      String route,
      MockHttpServletRequestBuilder request,
      RuntimeException thrown,
      int status,
      String code)
      throws Exception {
    orderFakes.repository.failure = thrown;

    mockMvc
        .perform(request)
        .andExpect(status().is(status))
        .andExpect(content().json("{\"code\":\"%s\"}".formatted(code)));
  }

  static Stream<Arguments> controlledFailures() {
    return Stream.of(
        Arguments.of(
            "POST /v1/order",
            order(VALID_ORDER),
            new OrderCreationException(422, "PSP_UNAVAILABLE"),
            422,
            "PSP_UNAVAILABLE"),
        Arguments.of(
            "POST /v1/order/modification",
            modification(VALID_MODIFICATION),
            new ModifyOrderException(409, "ORDER_NOT_PAID"),
            409,
            "ORDER_NOT_PAID"),
        Arguments.of(
            "GET /v1/report without an active merchant account",
            merchantGet("/v1/report" + PERIOD),
            new IllegalStateException("not reached: the merchant lookup answers first"),
            401,
            "MERCHANT_NOT_FOUND"),
        Arguments.of(
            "GET /v1/report over more than thirty days",
            operatorGet("/v1/report?from=2026-09-01&to=2026-10-01"),
            new IllegalStateException("not reached: the period is refused first"),
            400,
            "INVALID_REPORT_PERIOD"),
        Arguments.of(
            "GET /v1/report/{reportId} for a report never built",
            merchantGet("/v1/report/" + UUID.randomUUID()),
            new IllegalStateException("not reached: the report store answers first"),
            404,
            "REPORT_NOT_FOUND"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("bodyRoutes")
  void answersAnUnreadableBodyWithInvalidRequest(String route, String path) throws Exception {
    mockMvc
        .perform(merchantPost(path).content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(content().json("{\"code\":\"INVALID_REQUEST\"}"));
  }

  static Stream<Arguments> bodyRoutes() {
    return Stream.of(
        Arguments.of("POST /v1/order", "/v1/order"),
        Arguments.of("POST /v1/order/modification", "/v1/order/modification"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("unreadableParameters")
  void answersAnAbsentOrUnreadableParameterWithInvalidRequest(String route, String path)
      throws Exception {
    mockMvc
        .perform(operatorGet(path))
        .andExpect(status().isBadRequest())
        .andExpect(content().json("{\"code\":\"INVALID_REQUEST\"}"));
  }

  static Stream<Arguments> unreadableParameters() {
    return Stream.of(
        Arguments.of("GET /v1/report without from", "/v1/report?to=2026-09-30"),
        Arguments.of("GET /v1/report with an unreadable date", "/v1/report?from=today&to=today"),
        Arguments.of("GET /v1/report/{reportId} with an unreadable id", "/v1/report/latest"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("everyRoute")
  void answersAnUnexpectedFailureWithOneErrorLogLineTheResponseIdentifies(
      String route, MockHttpServletRequestBuilder request) throws Exception {
    orderFakes.repository.failure = new IllegalStateException("unexpected");
    Logger logger = (Logger) LoggerFactory.getLogger(GatewayErrorAdvice.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      MvcResult result =
          mockMvc.perform(request).andExpect(status().isInternalServerError()).andReturn();

      ErrorResponse body =
          JSON.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);
      assertThat(body.code()).isEqualTo("INTERNAL_ERROR");
      assertThat(body.correlationId()).isNotBlank();
      assertThat(appender.list).hasSize(1);
      ILoggingEvent event = appender.list.getFirst();
      assertThat(event.getLevel()).isEqualTo(Level.ERROR);
      assertThat(event.getMDCPropertyMap()).containsEntry("correlation_id", body.correlationId());
      assertThat(event.getThrowableProxy()).isNotNull();
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  static Stream<Arguments> everyRoute() {
    return Stream.of(
        Arguments.of("POST /v1/order", order(VALID_ORDER)),
        Arguments.of("POST /v1/order/modification", modification(VALID_MODIFICATION)),
        Arguments.of("GET /v1/report", operatorGet("/v1/report" + PERIOD)),
        Arguments.of("GET /v1/psps", merchantGet("/v1/psps")));
  }

  @Test
  void refusesAnOperatorCreatingAnOrderWithTheErrorBody() throws Exception {
    mockMvc
        .perform(
            post("/v1/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_ORDER)
                .requestAttr(
                    MerchantAuthenticationFilter.PRINCIPAL_ATTRIBUTE, GatewayPrincipal.operator()))
        .andExpect(status().isForbidden())
        .andExpect(content().json("{\"code\":\"MERCHANT_REQUIRED\"}"));
  }

  private static MockHttpServletRequestBuilder order(String body) {
    return merchantPost("/v1/order").content(body);
  }

  private static MockHttpServletRequestBuilder modification(String body) {
    return merchantPost("/v1/order/modification").content(body);
  }

  private static MockHttpServletRequestBuilder merchantPost(String path) {
    return post(path)
        .contentType(MediaType.APPLICATION_JSON)
        .requestAttr(
            MerchantAuthenticationFilter.PRINCIPAL_ATTRIBUTE,
            GatewayPrincipal.merchant(OrderServiceFakes.MERCHANT_ACCOUNT_ID));
  }

  private static MockHttpServletRequestBuilder merchantGet(String path) {
    return get(path)
        .requestAttr(
            MerchantAuthenticationFilter.PRINCIPAL_ATTRIBUTE,
            GatewayPrincipal.merchant(OrderServiceFakes.MERCHANT_ACCOUNT_ID));
  }

  private static MockHttpServletRequestBuilder operatorGet(String path) {
    return get(path)
        .requestAttr(MerchantAuthenticationFilter.PRINCIPAL_ATTRIBUTE, GatewayPrincipal.operator());
  }

  private static final class FailingLedger implements LedgerReportClient {
    @Override
    public BalanceReport platformReport(ReportPeriod period) {
      throw new IllegalStateException("Ledger unreachable");
    }

    @Override
    public BalanceReport merchantReport(String merchantCode, ReportPeriod period) {
      throw new IllegalStateException("Ledger unreachable");
    }
  }

  private static final class FailingMerchants
      implements com.outpost.gateway.report.repository.ReportRepository {
    @Override
    public java.util.Optional<String> findMerchantCode(long accountId) {
      return java.util.Optional.empty();
    }
  }

  private static final class FailingMerchantPsps implements MerchantPspRepository {
    @Override
    public boolean isPspEnabled(long merchantAccountId, long pspAccountId) {
      throw new IllegalStateException("configuration unreachable");
    }

    @Override
    public List<Account> findEnabledPsps(long merchantAccountId) {
      throw new IllegalStateException("configuration unreachable");
    }
  }
}
