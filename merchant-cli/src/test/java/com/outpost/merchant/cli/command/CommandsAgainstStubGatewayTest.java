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
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.ObjectMapper;

/** Each command sends the signed request the Gateway expects and prints what came back. */
class CommandsAgainstStubGatewayTest {
  private static final String API_KEY = "merchant-key";
  private static final String SECRET = "merchant-secret";
  private static final String OPERATOR_KEY = "operator-key";
  private static final String PSP_KEY = "psp-key";
  private static final String ORDER =
      """
      {"order_reference":"order-1","created_at":"2026-09-13T10:00:00Z",
       "payment_details":{"amount":4400,"currency":"EUR","tax_amount":924,"total_amount":5324,
       "payment_link":"http://psp/v1/DEMO_PSP/payment"},
       "order_lines":[{"order_line_reference":"line-1","merchant_line_reference":"EBOOK",
       "amount":1900,"tax_amount":399,"total_amount":2299,"tax_rate":"0.2100"},
       {"order_line_reference":"line-2","merchant_line_reference":"TSHIRT",
       "amount":2500,"tax_amount":525,"total_amount":3025,"tax_rate":"0.2100"}]}
      """;
  private static final String REPORT_ID = "3f1c2a54-9b0e-4d6f-8a7b-1c2d3e4f5a6b";
  private static final String REPORT =
      """
      {"from":"2026-09-01","to":"2026-09-30","accounts":[{"account_code":"DEMO_MERCHANT",
       "balance_accounts":[{"balance_account_code":"MERCHANT_PAYABLE",
       "balances":[{"currency":"EUR","balance":"76.00"}]},
       {"balance_account_code":"PENDING_FEE","balances":[]}]}]}
      """;

  private StubGateway gateway;
  private MerchantCommands merchantCommands;
  private OrderCommands orderCommands;

  @BeforeEach
  void startStub() throws Exception {
    Map<String, StubGateway.Answer> answers = new HashMap<>();
    gateway = new StubGateway(answers);
    answers.put("POST /v1/order", new StubGateway.Answer(201, ORDER));
    answers.put(
        "POST /v1/order/modification",
        new StubGateway.Answer(202, "{\"refund_reference\":\"refund-9\"}"));
    answers.put(
        "GET /v1/report", new StubGateway.Answer(200, "{\"report_url\":\"" + reportUrl() + "\"}"));
    answers.put("GET /v1/report/" + REPORT_ID, new StubGateway.Answer(200, REPORT));
    answers.put("POST /v1/DEMO_PSP/payment", new StubGateway.Answer(202, ""));
    MerchantCliProperties properties =
        new MerchantCliProperties(
            gateway.baseUrl(),
            OPERATOR_KEY,
            PSP_KEY,
            Map.of("DEMO_MERCHANT", new MerchantCredentials(API_KEY, SECRET)),
            List.of(
                new CatalogueItem("EBOOK", "E-book", 1900, "EUR", "DIGITAL_GOODS"),
                new CatalogueItem("TSHIRT", "T-shirt", 2500, "EUR", "PHYSICAL_GOODS")));
    HttpClient http = HttpClient.newHttpClient();
    ObjectMapper json = new ObjectMapper();
    GatewayClient gatewayClient = new GatewayClient(gateway.baseUrl(), http, json);
    ShellSession session = new ShellSession(properties.merchants());
    StoredMerchants merchants = new StoredMerchants(gateway.baseUrl().toString());
    merchantCommands = new MerchantCommands(merchants, gatewayClient, session, properties);
    orderCommands =
        new OrderCommands(
            properties,
            gatewayClient,
            new PspPaymentClient(PSP_KEY, http, json),
            merchants,
            session);
  }

  @AfterEach
  void stopStub() {
    gateway.close();
  }

  @Test
  void listsMerchantsMarkingTheCurrentOneAndThoseWithoutCredentials() {
    assertThat(merchantCommands.merchants())
        .isEqualTo(
            "* DEMO_MERCHANT  Demo Merchant\n"
                + "  OTHER_MERCHANT  Other Merchant  (no credentials configured)");
    assertThat(merchantCommands.use("OTHER_MERCHANT"))
        .isEqualTo("no credentials configured for OTHER_MERCHANT; see merchants");
    assertThat(merchantCommands.use("DEMO_MERCHANT")).isEqualTo("acting as DEMO_MERCHANT");
  }

  @Test
  void listsTheStoredPspsOfTheCurrentMerchantWithoutCallingTheGateway() {
    assertThat(merchantCommands.psps()).isEqualTo("DEMO_PSP  Demo PSP");
    assertThat(gateway.received()).isEmpty();
  }

  @Test
  void createsAnOrderFromCatalogueItemsAndPrintsThePaymentLink() throws Exception {
    String printed =
        orderCommands.order("DEMO_PSP", "EBOOK, TSHIRT", "NL", null, "s@example.test", "Shopper");

    StubGateway.Received request = gateway.received().getFirst();
    assertThat(request.path()).isEqualTo("/v1/order");
    assertThat(request.signature()).isEqualTo(signature(request.body()));
    var sent = new ObjectMapper().readTree(request.body());
    assertThat(sent.get("psp_code").asString()).isEqualTo("DEMO_PSP");
    assertThat(sent.get("merchant_reference").asString()).startsWith("cli-");
    assertThat(sent.get("idempotency_key").asString())
        .isEqualTo(sent.get("merchant_reference").asString());
    assertThat(sent.get("shopper_details").get("country").asString()).isEqualTo("NL");
    assertThat(sent.get("order_details").get("total_amount").asLong()).isEqualTo(4400);
    assertThat(sent.get("order_details").get("currency").asString()).isEqualTo("EUR");
    assertThat(sent.get("order_details").get("order_lines")).hasSize(2);
    assertThat(sent.get("order_details").get("order_lines").get(1).get("type").asString())
        .isEqualTo("PHYSICAL_GOODS");
    assertThat(printed)
        .isEqualTo(
            """
            order order-1
              EBOOK  net 19.00 EUR  tax 3.99 EUR (rate 0.2100)
              TSHIRT  net 25.00 EUR  tax 5.25 EUR (rate 0.2100)
            net 44.00 EUR  tax 9.24 EUR  total 53.24 EUR
            pay at http://psp/v1/DEMO_PSP/payment  (pay order-1)"""
                .stripIndent());
  }

  @Test
  void refusesAnUnknownCatalogueItemWithoutCallingTheGateway() {
    assertThat(orderCommands.order("DEMO_PSP", "GADGET", "NL", null, "s@x", "S"))
        .isEqualTo("unknown catalogue item GADGET; see catalogue");
    assertThat(gateway.received()).isEmpty();
  }

  @Test
  void paysAtThePspWithTheCardAndTheStoredPspReference() throws Exception {
    String printed = orderCommands.pay("order-1", "4000000000000002");

    StubGateway.Received request = gateway.received().getFirst();
    assertThat(request.path()).isEqualTo("/v1/DEMO_PSP/payment");
    assertThat(request.apiKey()).isEqualTo(PSP_KEY);
    var sent = new ObjectMapper().readTree(request.body());
    assertThat(sent.get("psp_reference").asString()).isEqualTo("41");
    assertThat(sent.get("card_number").asString()).isEqualTo("4000000000000002");
    assertThat(printed).startsWith("payment submitted for order-1");
  }

  @Test
  void printsWhyTheDatabaseCannotBeReadInsteadOfFailing() {
    MerchantRepository unreachable =
        new MerchantRepository() {
          @Override
          public List<Merchant> findActiveMerchants() {
            throw new DataAccessResourceFailureException("connection refused");
          }

          @Override
          public List<Psp> findEnabledPsps(String merchantCode) {
            throw new DataAccessResourceFailureException("connection refused");
          }

          @Override
          public Optional<OrderPayment> findOrderPayment(String orderReference) {
            throw new DataAccessResourceFailureException("connection refused");
          }
        };
    MerchantCommands commands =
        new MerchantCommands(
            unreachable,
            new GatewayClient(gateway.baseUrl(), HttpClient.newHttpClient(), new ObjectMapper()),
            new ShellSession(Map.of("DEMO_MERCHANT", new MerchantCredentials(API_KEY, SECRET))),
            new MerchantCliProperties(
                gateway.baseUrl(),
                OPERATOR_KEY,
                PSP_KEY,
                Map.of("DEMO_MERCHANT", new MerchantCredentials(API_KEY, SECRET)),
                List.of(new CatalogueItem("EBOOK", "E-book", 1900, "EUR", "DIGITAL_GOODS"))));

    assertThat(commands.merchants())
        .isEqualTo("cannot read Outpost's database: connection refused");
    assertThat(commands.psps()).isEqualTo("cannot read Outpost's database: connection refused");
  }

  @Test
  void refusesToPayAnOrderThePspHasNotAccepted() {
    assertThat(orderCommands.pay("order-unpaid", OrderCommands.APPROVED_CARD))
        .isEqualTo("no payment to pay for order-unpaid; create the order first");
    assertThat(gateway.received()).isEmpty();
  }

  @Test
  void refundsTheWholeOrderAndPrintsTheRefundReference() throws Exception {
    assertThat(orderCommands.refund("order-1")).isEqualTo("refund accepted: refund-9");

    StubGateway.Received request = gateway.received().getFirst();
    assertThat(request.path()).isEqualTo("/v1/order/modification");
    assertThat(request.signature()).isEqualTo(signature(request.body()));
    var sent = new ObjectMapper().readTree(request.body());
    assertThat(sent.get("order_reference").asString()).isEqualTo("order-1");
    assertThat(sent.get("type").asString()).isEqualTo("REFUND");
    assertThat(sent.get("idempotency_key").asString()).startsWith("refund-");
  }

  @Test
  void printsTheGatewaysStatusAndCodeWhenItRefuses() throws Exception {
    try (StubGateway refusing =
        new StubGateway(
            Map.of(
                "POST /v1/order/modification",
                new StubGateway.Answer(409, "{\"code\":\"ORDER_NOT_PAID\"}")))) {
      GatewayClient client =
          new GatewayClient(refusing.baseUrl(), HttpClient.newHttpClient(), new ObjectMapper());
      OrderCommands commands =
          new OrderCommands(
              new MerchantCliProperties(
                  refusing.baseUrl(),
                  OPERATOR_KEY,
                  PSP_KEY,
                  Map.of("DEMO_MERCHANT", new MerchantCredentials(API_KEY, SECRET)),
                  List.of(new CatalogueItem("EBOOK", "E-book", 1900, "EUR", "DIGITAL_GOODS"))),
              client,
              new PspPaymentClient(PSP_KEY, HttpClient.newHttpClient(), new ObjectMapper()),
              new StoredMerchants(refusing.baseUrl().toString()),
              new ShellSession(Map.of("DEMO_MERCHANT", new MerchantCredentials(API_KEY, SECRET))));

      assertThat(commands.refund("order-1")).isEqualTo("HTTP 409 ORDER_NOT_PAID");
    }
  }

  @Test
  void requestsTheReportThenReadsItAtItsUrlAsTheMerchantOrAsTheOperator() {
    String printed =
        """
        report %s
        DEMO_MERCHANT
          MERCHANT_PAYABLE
            76.00 EUR
          PENDING_FEE  (no balances)"""
            .formatted(reportUrl())
            .stripIndent();

    assertThat(merchantCommands.report("2026-09-01", "2026-09-30")).isEqualTo(printed);
    assertThat(merchantCommands.reportPlatform("2026-09-01", "2026-09-30")).isEqualTo(printed);

    List<StubGateway.Received> requests = gateway.received();
    assertThat(requests).hasSize(4);
    assertThat(requests.get(0).path()).isEqualTo("/v1/report");
    assertThat(requests.get(0).query()).isEqualTo("from=2026-09-01&to=2026-09-30");
    assertThat(requests.get(0).apiKey()).isEqualTo(API_KEY);
    assertThat(requests.get(0).signature()).isEqualTo(signature(""));
    assertThat(requests.get(1).path()).isEqualTo("/v1/report/" + REPORT_ID);
    assertThat(requests.get(1).apiKey()).isEqualTo(API_KEY);
    assertThat(requests.get(1).signature()).isEqualTo(signature(""));
    assertThat(requests.get(2).path()).isEqualTo("/v1/report");
    assertThat(requests.get(2).query()).isEqualTo("from=2026-09-01&to=2026-09-30");
    assertThat(requests.get(2).apiKey()).isEqualTo(OPERATOR_KEY);
    assertThat(requests.get(2).signature()).isNull();
    assertThat(requests.get(3).path()).isEqualTo("/v1/report/" + REPORT_ID);
    assertThat(requests.get(3).apiKey()).isEqualTo(OPERATOR_KEY);
    assertThat(requests.get(3).signature()).isNull();
  }

  @Test
  void refusesAnUnreadableDateWithoutCallingTheGateway() {
    assertThat(merchantCommands.report("2026-09-01", "yesterday"))
        .isEqualTo("invalid date yesterday; use YYYY-MM-DD");
    assertThat(gateway.received()).isEmpty();
  }

  private String reportUrl() {
    return gateway.baseUrl() + "/v1/report/" + REPORT_ID;
  }

  private static String signature(String body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException(exception);
    }
  }

  /** The merchants and the one paid order the stubbed platform "stores". */
  private record StoredMerchants(String pspBaseUrl) implements MerchantRepository {
    @Override
    public List<Merchant> findActiveMerchants() {
      return List.of(
          new Merchant("DEMO_MERCHANT", "Demo Merchant"),
          new Merchant("OTHER_MERCHANT", "Other Merchant"));
    }

    @Override
    public List<Psp> findEnabledPsps(String merchantCode) {
      return merchantCode.equals("DEMO_MERCHANT")
          ? List.of(new Psp("DEMO_PSP", "Demo PSP"))
          : List.of();
    }

    @Override
    public Optional<OrderPayment> findOrderPayment(String orderReference) {
      return orderReference.equals("order-1")
          ? Optional.of(new OrderPayment("41", pspBaseUrl + "/v1/DEMO_PSP/payment"))
          : Optional.empty();
    }
  }
}
