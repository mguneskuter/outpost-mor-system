package com.outpost.backoffice.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.outpost.backoffice.configuration.BackOfficeProperties;
import com.outpost.backoffice.gateway.BalanceReport;
import com.outpost.backoffice.gateway.CreateOrderRequest;
import com.outpost.backoffice.gateway.CreatedOrder;
import com.outpost.backoffice.gateway.GatewayClient;
import com.outpost.backoffice.merchant.Merchant;
import com.outpost.backoffice.merchant.MerchantRepository;
import com.outpost.backoffice.merchant.Psp;
import com.outpost.backoffice.payment.OrderLine;
import com.outpost.backoffice.payment.Payment;
import com.outpost.backoffice.payment.PaymentEvent;
import com.outpost.backoffice.payment.PaymentJournalLine;
import com.outpost.backoffice.payment.PaymentRepository;
import com.outpost.backoffice.psp.PspPaymentClient;
import com.outpost.backoffice.register.RegisterBalance;
import com.outpost.backoffice.register.RegisterBalanceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Each tab shows what the platform stores and sends what the merchant would. */
@WebMvcTest({PaymentsController.class, BalancesController.class, ReportsController.class})
@EnableConfigurationProperties(BackOfficeProperties.class)
@TestPropertySource(
    properties = {
      "backoffice.gateway-base-url=http://gateway",
      "backoffice.operator-api-key=operator",
      "backoffice.psp-api-key=psp",
      "backoffice.merchants.DEMO_MERCHANT.api-key=key",
      "backoffice.merchants.DEMO_MERCHANT.hmac-secret=secret",
      "backoffice.catalogue[0].sku=EBOOK",
      "backoffice.catalogue[0].name=E-book",
      "backoffice.catalogue[0].amount=1900",
      "backoffice.catalogue[0].currency=EUR",
      "backoffice.catalogue[0].type=DIGITAL_GOODS",
      "backoffice.catalogue[1].sku=TSHIRT",
      "backoffice.catalogue[1].name=T-shirt",
      "backoffice.catalogue[1].amount=2500",
      "backoffice.catalogue[1].currency=EUR",
      "backoffice.catalogue[1].type=PHYSICAL_GOODS"
    })
class PagesTest {
  private static final Instant CREATED_AT = Instant.parse("2026-09-13T10:00:00Z");
  private static final BigDecimal RATE = new BigDecimal("0.2100");
  private static final Payment CAPTURED =
      new Payment(
          "order-1",
          "41",
          "DEMO_MERCHANT",
          "Demo Merchant",
          "Demo PSP",
          "CAPTURED",
          "EUR",
          5324,
          4400,
          924,
          220L,
          "NL",
          List.of("DIGITAL_GOODS", "PHYSICAL_GOODS"),
          CREATED_AT);
  private static final Payment UNPAID =
      new Payment(
          "order-2",
          null,
          "DEMO_MERCHANT",
          "Demo Merchant",
          "Demo PSP 2",
          null,
          "EUR",
          2299,
          1900,
          399,
          null,
          "DE",
          List.of("DIGITAL_GOODS"),
          CREATED_AT);

  @Autowired private MockMvc mvc;
  @MockitoBean private MerchantRepository merchants;
  @MockitoBean private PaymentRepository payments;
  @MockitoBean private RegisterBalanceRepository balances;
  @MockitoBean private GatewayClient gateway;
  @MockitoBean private PspPaymentClient psp;

  @BeforeEach
  void storedMerchants() {
    when(merchants.findActiveMerchants())
        .thenReturn(
            List.of(
                new Merchant("DEMO_MERCHANT", "Demo Merchant"),
                new Merchant("OTHER_MERCHANT", "Other Merchant")));
    when(merchants.findEnabledPsps("DEMO_MERCHANT"))
        .thenReturn(List.of(new Psp("DEMO_PSP", "Demo PSP")));
    when(merchants.findCountryCodes()).thenReturn(List.of("DE", "NL"));
  }

  @Test
  void paymentsTabListsPaymentsWithTheStatusTheLedgerBookedAndRefundsOnlyCapturedOnes()
      throws Exception {
    when(payments.findPayments()).thenReturn(List.of(CAPTURED, UNPAID));

    String page =
        mvc.perform(get("/payments"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(page)
        .contains("order-1")
        .contains(">captured<")
        .contains(">53.24<")
        .contains(">2.20<")
        .contains("DIGITAL_GOODS, PHYSICAL_GOODS")
        .contains("/payments/order-1/refund")
        .contains(">PSP error<")
        .doesNotContain("/payments/order-2/refund")
        .doesNotContain("OTHER_MERCHANT");
  }

  @Test
  void creatingPaymentCreatesTheOrderAtTheGatewayThenPaysItAtThePspWithTheCard() throws Exception {
    CreatedOrder created =
        new CreatedOrder(
            "order-1",
            new CreatedOrder.PaymentDetails(
                4400, "EUR", 924, 5324, "http://psp/v1/DEMO_PSP/payment"),
            List.of());
    when(gateway.createOrder(any(), any())).thenReturn(created);
    when(payments.findPayments()).thenReturn(List.of(CAPTURED));

    mvc.perform(
            post("/payments")
                .param("merchant", "DEMO_MERCHANT")
                .param("psp", "DEMO_PSP")
                .param("items", "EBOOK", "TSHIRT")
                .param("country", "NL")
                .param("card", "4000000000000002"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/payments?merchant=DEMO_MERCHANT"));

    org.mockito.ArgumentCaptor<CreateOrderRequest> request =
        org.mockito.ArgumentCaptor.forClass(CreateOrderRequest.class);
    verify(gateway).createOrder(any(), request.capture());
    assertThat(request.getValue().pspCode()).isEqualTo("DEMO_PSP");
    assertThat(request.getValue().orderDetails().totalAmount()).isEqualTo(4400);
    assertThat(request.getValue().orderDetails().orderLines()).hasSize(2);
    verify(psp).pay("http://psp/v1/DEMO_PSP/payment", "41", "4000000000000002");
  }

  @Test
  void paymentBreakdownShowsItsEventsItsJournalLinesAndTheRegistersItTouched() throws Exception {
    when(payments.findPayments()).thenReturn(List.of(CAPTURED));
    when(payments.findPaymentEvents("order-1"))
        .thenReturn(
            List.of(
                new PaymentEvent(1, "PAYMENT", "order-1", "EUR", 5324, "ORDER_CREATED", CREATED_AT),
                new PaymentEvent(3, "CAPTURE", "capture-1", "EUR", 5324, "CAPTURED", CREATED_AT)));
    when(payments.findPaymentJournalLines("order-1"))
        .thenReturn(
            List.of(
                line(1, "FEE_PENDING", "DEMO_MERCHANT", "Demo Merchant", "PENDING_FEE", 220),
                line(1, "FEE_PENDING", "OUTPOST", "Outpost", "PENDING_FEE", -220),
                line(2, "CAPTURE", "DEMO_MERCHANT", "Demo Merchant", "PENDING_FEE", -220),
                line(2, "CAPTURE", "DEMO_MERCHANT", "Demo Merchant", "MERCHANT_PAYABLE", -4180)));
    when(payments.findOrderLines("order-1"))
        .thenReturn(
            List.of(
                new OrderLine("line-1", "EBOOK", "DIGITAL_GOODS", 1900, 399, RATE, true),
                new OrderLine("line-2", "TSHIRT", "PHYSICAL_GOODS", 2500, 525, RATE, false)));

    String page =
        mvc.perform(get("/payments/order-1"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(page)
        .contains(">line-1<")
        .contains(">refunded<")
        .contains(">line-2<")
        .contains("name=\"lines\" value=\"line-2\"")
        .doesNotContain("name=\"lines\" value=\"line-1\"")
        .contains(">Refund line<")
        .contains(">Refund every remaining line<")
        .contains(">capture-1<")
        .contains(">CAPTURED<")
        .contains(">FEE_PENDING<")
        .contains(">41.80<")
        .contains(">0.00<")
        .contains(">41.80 Cr<");
    String balanceAccounts = page.substring(page.indexOf(">Balance accounts</h2>"));
    assertThat(balanceAccounts)
        .contains(">MERCHANT_PAYABLE<")
        .contains(">46.20<")
        .contains(">2.20<");
  }

  @Test
  void refundingChosenLinesFromThePaymentPageSendsThemAndReturnsToIt() throws Exception {
    when(gateway.refund(any(), eq("order-1"), any(), eq(List.of("line-2")))).thenReturn("refund-9");

    mvc.perform(
            post("/payments/order-1/refund")
                .param("merchant", "DEMO_MERCHANT")
                .param("origin", "payment")
                .param("lines", "line-2"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/payments/order-1?merchant=DEMO_MERCHANT"));

    verify(gateway).refund(any(), eq("order-1"), any(), eq(List.of("line-2")));
  }

  @Test
  void refundingFromTheListSendsNoLinesSoEveryRemainingLineIsRefunded() throws Exception {
    when(gateway.refund(any(), eq("order-1"), any(), eq(List.of()))).thenReturn("refund-9");

    mvc.perform(post("/payments/order-1/refund").param("merchant", "DEMO_MERCHANT"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/payments?merchant=DEMO_MERCHANT"));

    verify(gateway).refund(any(), eq("order-1"), any(), eq(List.of()));
  }

  private static PaymentJournalLine line(
      long entryId, String entryType, String account, String name, String register, long quantity) {
    return new PaymentJournalLine(
        entryId,
        entryType,
        "ORDER_CREATED",
        "order-1",
        CREATED_AT,
        account,
        name,
        register,
        "EUR",
        quantity);
  }

  @Test
  void balancesTabShowsEachBalanceAccountPerCurrencyWithSummaryAndTotals() throws Exception {
    when(balances.findAccountTypeCodes()).thenReturn(List.of("MERCHANT", "PLATFORM"));
    when(balances.findAccountCodes(List.of("MERCHANT"))).thenReturn(List.of("DEMO_MERCHANT"));
    when(balances.findRegisterTypeCodes()).thenReturn(List.of("MERCHANT_PAYABLE", "PENDING_FEE"));
    when(balances.findBalances(List.of("MERCHANT"), List.of(), List.of()))
        .thenReturn(
            List.of(
                new RegisterBalance(
                    "MERCHANT",
                    "DEMO_MERCHANT",
                    "Demo Merchant",
                    "MERCHANT_PAYABLE",
                    "EUR",
                    0,
                    2375),
                new RegisterBalance(
                    "MERCHANT", "DEMO_MERCHANT", "Demo Merchant", "MERCHANT_PAYABLE", "USD", 0, 0),
                new RegisterBalance(
                    "MERCHANT", "DEMO_MERCHANT", "Demo Merchant", "PENDING_FEE", "EUR", 125, 0),
                new RegisterBalance(
                    "MERCHANT", "DEMO_MERCHANT", "Demo Merchant", "PENDING_FEE", "USD", 0, 0)));

    String page =
        mvc.perform(get("/balances").param("accountType", "MERCHANT").param("account", ""))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(page)
        .contains(">MERCHANT_PAYABLE  EUR<")
        .contains(">23.75 Cr<")
        .contains(">1.25 Dr<")
        .contains(">Total EUR<")
        .contains(">22.50 Cr<")
        .contains(">0.00<");
  }

  @Test
  void reportsTabReadsThePlatformReportForThePeriod() throws Exception {
    when(gateway.requestPlatformReport(
            eq("operator"), eq(LocalDate.parse("2026-09-01")), eq(LocalDate.parse("2026-09-30"))))
        .thenReturn("http://gateway/v1/report/r-1");
    when(gateway.readPlatformReport("operator", "http://gateway/v1/report/r-1"))
        .thenReturn(
            new BalanceReport(
                LocalDate.parse("2026-09-01"),
                LocalDate.parse("2026-09-30"),
                List.of(
                    new BalanceReport.Account(
                        "TAX_AUTHORITY_NL",
                        List.of(
                            new BalanceReport.BalanceAccount(
                                "TAX_PAYABLE",
                                List.of(new BalanceReport.Balance("EUR", "16.80"))))))));

    String page =
        mvc.perform(
                get("/reports")
                    .param("scope", "PLATFORM")
                    .param("from", "2026-09-01")
                    .param("to", "2026-09-30"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(page)
        .contains("TAX_AUTHORITY_NL")
        .contains("TAX_PAYABLE")
        .contains(">16.80<")
        .contains(">Total<");
  }
}
