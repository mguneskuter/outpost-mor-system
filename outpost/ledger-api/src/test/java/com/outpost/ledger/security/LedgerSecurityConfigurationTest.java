package com.outpost.ledger.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.hmac.HmacSha256;
import com.outpost.ledger.accountingrequest.api.AccountingRequestController;
import com.outpost.ledger.accountingrequest.service.AccountingRequestService;
import com.outpost.ledger.report.api.BalanceReportController;
import com.outpost.ledger.report.service.BalanceReport;
import com.outpost.ledger.report.service.BalanceReportService;
import jakarta.servlet.Filter;
import java.net.URI;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringJUnitWebConfig(LedgerSecurityConfigurationTest.LedgerRoutes.class)
@TestPropertySource(
    properties =
        "outpost.ledger.gateway-hmac-secret=" + LedgerSecurityConfigurationTest.GATEWAY_SECRET)
class LedgerSecurityConfigurationTest {

  static final String GATEWAY_SECRET = "gateway-secret";
  private static final HmacKey GATEWAY_KEY = HmacKey.fromUtf8(GATEWAY_SECRET);
  private static final String UNAUTHENTICATED = "{\"code\":\"UNAUTHENTICATED\"}";

  @Autowired private WebApplicationContext context;

  @Autowired
  @Qualifier(AbstractSecurityWebApplicationInitializer.DEFAULT_FILTER_NAME)
  private Filter securityFilterChain;

  private MockMvc mockMvc;

  @BeforeEach
  void setUpMockMvc() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilterChain).build();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/v1/accounting-request",
        "/v1/%61ccounting-request",
        "/v1/accounting-request.",
        "/V1/ACCOUNTING-REQUEST"
      })
  void rejectsUnsignedPaymentCreationUnderEveryPathVariant(String path) throws Exception {
    mockMvc
        .perform(request(HttpMethod.POST, URI.create(path)).contentType("application/json"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().json(UNAUTHENTICATED));
  }

  @Test
  void authorizesAnEncodedPathAsTheRouteItDispatchesTo() throws Exception {
    signed(HttpMethod.POST, "/v1/%61ccounting-request", "{}", GATEWAY_KEY)
        .andExpect(status().isAccepted());
  }

  @Test
  void rejectsPathParameterVariantBeforeAnyRouteIsMatched() throws Exception {
    signed(HttpMethod.POST, "/v1/accounting-request;x=1", "{}", GATEWAY_KEY)
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @MethodSource("callerRoutes")
  void acceptsTheOwningCallersSignatureOnEachRoute(CallerRoute route) throws Exception {
    signed(route.method(), route.path(), route.body(), route.owner())
        .andExpect(status().is(route.successStatus()));
  }

  @Test
  void rejectsSignatureThatDoesNotMatchTheBody() throws Exception {
    mockMvc
        .perform(
            request(HttpMethod.POST, URI.create("/v1/accounting-request"))
                .contentType("application/json")
                .content("{}")
                .header("X-Outpost-Signature", signature(GATEWAY_KEY, "{\"changed\":true}")))
        .andExpect(status().isUnauthorized())
        .andExpect(content().json(UNAUTHENTICATED));
  }

  @Test
  void rejectsUnsignedRequestToMappedRouteThatIsNotPublic() throws Exception {
    mockMvc
        .perform(request(HttpMethod.GET, URI.create(UnlistedController.PATH)))
        .andExpect(status().isUnauthorized())
        .andExpect(content().json(UNAUTHENTICATED));
  }

  @Test
  void rejectsSignedRequestToMappedRouteNoCallerIsGranted() throws Exception {
    signed(HttpMethod.GET, UnlistedController.PATH, "", GATEWAY_KEY)
        .andExpect(status().isForbidden());
  }

  @Test
  void permitsOnlyTheHealthProbesWithoutSignature() {
    assertThat(LedgerSecurityConfiguration.PUBLIC_PATHS).containsExactly("/livez", "/readyz");
  }

  static Stream<CallerRoute> callerRoutes() {
    return Stream.of(
        new CallerRoute(HttpMethod.POST, "/v1/accounting-request", "{}", GATEWAY_KEY, 202),
        new CallerRoute(HttpMethod.GET, "/v1/report/balance/tax", "", GATEWAY_KEY, 200),
        new CallerRoute(HttpMethod.GET, "/v1/report/balance/merchant", "", GATEWAY_KEY, 200));
  }

  /** A route, the caller granted it, and the status its controller returns on success. */
  record CallerRoute(
      HttpMethod method, String path, String body, HmacKey owner, int successStatus) {}

  private ResultActions signed(HttpMethod method, String path, String body, HmacKey key)
      throws Exception {
    return mockMvc.perform(
        request(method, URI.create(path))
            .contentType("application/json")
            .content(body)
            .header("X-Outpost-Signature", signature(key, body)));
  }

  private static String signature(HmacKey key, String body) {
    return HmacSha256.signUtf8(key, body).toBase64();
  }

  @Configuration(proxyBeanMethods = false)
  @EnableWebMvc
  @EnableWebSecurity
  @EnableConfigurationProperties(LedgerAuthenticationProperties.class)
  @Import({
    LedgerSecurityConfiguration.class,
    AccountingRequestController.class,
    BalanceReportController.class,
    UnlistedController.class
  })
  static class LedgerRoutes {

    @Bean
    AccountingRequestService accountingRequestService() {
      return mock(AccountingRequestService.class);
    }

    @Bean
    BalanceReportService balanceReportService() {
      BalanceReportService service = mock(BalanceReportService.class);
      when(service.tax()).thenReturn(new BalanceReport(List.of()));
      when(service.merchant()).thenReturn(new BalanceReport(List.of()));
      return service;
    }
  }

  @RestController
  static class UnlistedController {
    static final String PATH = "/v1/unlisted";

    @GetMapping(PATH)
    String reached() {
      return "reached";
    }
  }
}
