package com.outpost.merchant.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.merchant.cli.configuration.MerchantCliProperties;
import com.outpost.merchant.cli.configuration.MerchantCliProperties.CatalogueItem;
import com.outpost.merchant.cli.configuration.MerchantCliProperties.MerchantCredentials;
import com.outpost.merchant.cli.gateway.GatewayClient;
import com.outpost.merchant.cli.merchant.Merchant;
import com.outpost.merchant.cli.merchant.MerchantRepository;
import com.outpost.merchant.cli.merchant.OrderPayment;
import com.outpost.merchant.cli.merchant.Psp;
import com.outpost.merchant.cli.psp.PspPaymentClient;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** The guided session asks step by step, runs the chosen command, and asks again. */
class GuidedShellTest {

  private StubGateway gateway;
  private MerchantCliProperties properties;
  private MerchantCommands merchantCommands;
  private OrderCommands orderCommands;
  private ShellSession session;

  @BeforeEach
  void startStub() throws Exception {
    Map<String, StubGateway.Answer> answers = new HashMap<>();
    gateway = new StubGateway(answers);
    answers.put("POST /v1/order", new StubGateway.Answer(201, createdOrder(gateway.baseUrl())));
    answers.put(
        "POST /v1/order/modification",
        new StubGateway.Answer(202, "{\"refund_reference\":\"refund-9\"}"));
    answers.put(
        "GET /v1/report/balance/merchant", new StubGateway.Answer(200, "{\"accounts\":[]}"));
    answers.put("POST /v1/DEMO_PSP/payment", new StubGateway.Answer(202, ""));
    properties =
        new MerchantCliProperties(
            gateway.baseUrl(),
            "operator-key",
            "psp-key",
            Map.of("DEMO_MERCHANT", new MerchantCredentials("key", "secret")),
            List.of(
                new CatalogueItem("EBOOK", "E-book", 1900, "EUR", "DIGITAL_GOODS"),
                new CatalogueItem("TSHIRT", "T-shirt", 2500, "EUR", "PHYSICAL_GOODS")));
    HttpClient http = HttpClient.newHttpClient();
    ObjectMapper json = new ObjectMapper();
    GatewayClient client = new GatewayClient(gateway.baseUrl(), http, json);
    session = new ShellSession(properties.merchants());
    StoredMerchants merchants = new StoredMerchants(gateway.baseUrl().toString());
    merchantCommands = new MerchantCommands(merchants, client, session, properties);
    orderCommands =
        new OrderCommands(
            properties, client, new PspPaymentClient("psp-key", http, json), merchants, session);
  }

  @AfterEach
  void stopStub() {
    gateway.close();
  }

  @Test
  void walksAnOrderThroughCreationPaymentAndRefundThenAsksAgain() {
    String printed =
        run(
            """
            1
            1
            1
            NL

            1,2
            2

            1
            3

            4
            0
            """);

    assertThat(printed)
        .contains("Which merchant?")
        .contains("1) DEMO_MERCHANT  Demo Merchant")
        .contains("acting as DEMO_MERCHANT")
        .contains("What next?")
        .contains("Which PSP?")
        .contains("Shopper country (ISO code) [NL]:")
        .contains("Which items? Several as 1,3")
        .contains("order order-1")
        .contains("pay at")
        .contains("Order reference [order-1]:")
        .contains("Which test card?")
        .contains("payment submitted for order-1")
        .contains("refund accepted: refund-9")
        .contains("nothing owed")
        .contains("Bye.");
    List<String> paths = gateway.received().stream().map(StubGateway.Received::path).toList();
    assertThat(paths)
        .containsExactly(
            "/v1/order",
            "/v1/DEMO_PSP/payment",
            "/v1/order/modification",
            "/v1/report/balance/merchant");
    assertThat(gateway.received().get(2).body()).contains("\"order_reference\":\"order-1\"");
  }

  @Test
  void asksAgainAfterAnswerThatIsNotChoice() {
    String printed =
        run(
            """
            9
            x
            1
            0
            """);

    assertThat(printed)
        .contains("Answer with a number between 1 and 1.")
        .contains("acting as DEMO_MERCHANT")
        .contains("Bye.");
  }

  @Test
  void refusesToPayBeforeAnyOrderExists() {
    String printed =
        run(
            """
            1
            2

            0
            """);

    assertThat(printed).contains("Order reference: ").contains("No order yet; create one first.");
    assertThat(gateway.received()).isEmpty();
  }

  @Test
  void stopsWhenTheInputEnds() {
    assertThat(run("1\n")).contains("acting as DEMO_MERCHANT").contains("Bye.");
  }

  /** The Gateway's answer to the created order, paid at the stub's own payment page. */
  private static String createdOrder(URI stubBaseUrl) {
    return """
        {"order_reference":"order-1","created_at":"2026-09-13T10:00:00Z",
         "payment_details":{"amount":4400,"currency":"EUR","tax_amount":924,"total_amount":5324,
         "payment_link":"%s/v1/DEMO_PSP/payment"},
         "order_lines":[{"order_line_reference":"line-1","merchant_line_reference":"EBOOK",
         "amount":1900,"tax_amount":399,"total_amount":2299,"tax_rate":"0.2100"},
         {"order_line_reference":"line-2","merchant_line_reference":"TSHIRT",
         "amount":2500,"tax_amount":525,"total_amount":3025,"tax_rate":"0.2100"}]}
        """
        .formatted(stubBaseUrl);
  }

  private String run(String script) {
    StringWriter printed = new StringWriter();
    new GuidedShell(
            new BufferedReader(new StringReader(script)),
            new PrintWriter(printed, true),
            new StoredMerchants(gateway.baseUrl().toString()),
            session,
            properties,
            merchantCommands,
            orderCommands)
        .run();
    return printed.toString();
  }

  private record StoredMerchants(String pspBaseUrl) implements MerchantRepository {
    @Override
    public List<Merchant> findActiveMerchants() {
      return List.of(
          new Merchant("DEMO_MERCHANT", "Demo Merchant"),
          new Merchant("OTHER_MERCHANT", "Other Merchant"));
    }

    @Override
    public List<Psp> findEnabledPsps(String merchantCode) {
      return List.of(new Psp("DEMO_PSP", "Demo PSP"));
    }

    @Override
    public Optional<OrderPayment> findOrderPayment(String orderReference) {
      return orderReference.equals("order-1")
          ? Optional.of(new OrderPayment("41", pspBaseUrl + "/v1/DEMO_PSP/payment"))
          : Optional.empty();
    }
  }
}
