package com.outpost.integration.psp.simulator;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.outpost.integration.psp.CreatePspOrderRequest;
import com.outpost.integration.psp.CreatePspOrderResult;
import com.outpost.integration.psp.PspClient;
import com.outpost.integration.psp.PspResultCodes;
import com.outpost.integration.psp.RefundPspOrderLine;
import com.outpost.integration.psp.RefundPspOrderRequest;
import com.outpost.integration.psp.RefundPspOrderResult;
import com.outpost.integration.psp.UnknownPspResultException;
import com.outpost.integration.psp.simulator.repository.PspConfigurationRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Calls the PSP simulator's HTTP protocol with the configuration stored for each PSP code. */
public final class SimulatorPspClient implements PspClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(SimulatorPspClient.class);
  private final PspConfigurationRepository configurations;

  /** Creates a client backed by the supplied configuration lookup. */
  public SimulatorPspClient(PspConfigurationRepository configurations) {
    this.configurations = configurations;
  }

  /** A PSP code with no stored configuration is rejected without a call. */
  @Override
  public CreatePspOrderResult createOrder(CreatePspOrderRequest request) {
    return configurations
        .findPspConfigurationByPspCode(request.pspCode())
        .map(configuration -> createOrderAt(configuration, request))
        .orElseGet(() -> new CreatePspOrderResult(null, null, PspResultCodes.REJECTED));
  }

  /** A PSP code with no stored configuration is rejected without a call. */
  @Override
  public RefundPspOrderResult refund(RefundPspOrderRequest request) {
    return configurations
        .findPspConfigurationByPspCode(request.pspCode())
        .map(configuration -> refundAt(configuration, request))
        .orElseGet(
            () -> new RefundPspOrderResult(request.pspReference(), null, PspResultCodes.REJECTED));
  }

  private CreatePspOrderResult createOrderAt(
      PspConfiguration configuration, CreatePspOrderRequest request) {
    return post(
            configuration,
            "/order",
            SimulatorOrderRequest.from(request),
            SimulatorOrderResponse.class)
        .map(
            response ->
                new CreatePspOrderResult(
                    response.pspReference(), response.paymentUrl(), PspResultCodes.ACCEPTED))
        .orElseGet(() -> new CreatePspOrderResult(null, null, PspResultCodes.REJECTED));
  }

  private RefundPspOrderResult refundAt(
      PspConfiguration configuration, RefundPspOrderRequest request) {
    return post(
            configuration,
            "/refund",
            SimulatorRefundRequest.from(request),
            SimulatorRefundResponse.class)
        .map(
            response ->
                new RefundPspOrderResult(
                    request.pspReference(),
                    response.pspRefundReference(),
                    response.accepted() ? PspResultCodes.ACCEPTED : PspResultCodes.REJECTED))
        .orElseGet(
            () -> new RefundPspOrderResult(request.pspReference(), null, PspResultCodes.REJECTED));
  }

  /**
   * Returns the simulator's answer, or empty when it refused the call with a 4xx status.
   *
   * @throws UnknownPspResultException when the call timed out, failed in transport, answered with a
   *     5xx status, or answered with an empty body
   */
  private <T> Optional<T> post(
      PspConfiguration configuration, String route, Object body, Class<T> type) {
    T response;
    try {
      response =
          client(configuration)
              .post()
              .uri(URI.create("/v1/" + configuration.code() + route))
              .header("X-Outpost-Api-Key", configuration.apiKey())
              .body(body)
              .retrieve()
              .body(type);
    } catch (HttpClientErrorException refused) {
      LOGGER.warn(
          "PSP refused the call pspCode={} route={} status={}",
          configuration.code(),
          route,
          refused.getStatusCode().value());
      return Optional.empty();
    } catch (RestClientException failure) {
      LOGGER.warn(
          "PSP call result unknown pspCode={} route={}", configuration.code(), route, failure);
      throw new UnknownPspResultException(failure);
    }
    if (response == null) {
      LOGGER.warn("PSP answered without a body pspCode={} route={}", configuration.code(), route);
      throw new UnknownPspResultException(null);
    }
    return Optional.of(response);
  }

  private static RestClient client(PspConfiguration configuration) {
    return RestClient.builder()
        .requestFactory(requestFactory(configuration))
        .baseUrl(configuration.baseUrl())
        .build();
  }

  private static JdkClientHttpRequestFactory requestFactory(PspConfiguration configuration) {
    HttpClient http =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(configuration.connectTimeoutMillis()))
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(Duration.ofMillis(configuration.readTimeoutMillis()));
    return factory;
  }

  private record SimulatorOrderRequest(
      @JsonProperty("payment_reference") String paymentReference, long amount, String currency) {
    static SimulatorOrderRequest from(CreatePspOrderRequest request) {
      return new SimulatorOrderRequest(
          request.paymentReference(),
          request.amount().quantity(),
          request.amount().currency().getCurrencyCode());
    }
  }

  private record SimulatorRefundRequest(
      @JsonProperty("psp_reference") String pspReference,
      @JsonProperty("payment_reference") String paymentReference,
      @JsonProperty("refund_reference") String refundReference,
      long amount,
      String currency,
      @JsonProperty("refund_lines") List<SimulatorRefundLine> refundLines) {
    static SimulatorRefundRequest from(RefundPspOrderRequest request) {
      return new SimulatorRefundRequest(
          request.pspReference(),
          request.orderReference(),
          request.refundReference(),
          request.amount().quantity(),
          request.amount().currency().getCurrencyCode(),
          request.lines().stream().map(SimulatorRefundLine::from).toList());
    }
  }

  private record SimulatorRefundLine(
      @JsonProperty("order_line_reference") String orderLineReference,
      @JsonProperty("tax_rate") String taxRate,
      @JsonProperty("net_amount") long netAmount,
      @JsonProperty("gross_amount") long grossAmount) {
    static SimulatorRefundLine from(RefundPspOrderLine line) {
      return new SimulatorRefundLine(
          line.orderLineReference(),
          line.taxRate().toPlainString(),
          line.netAmount().quantity(),
          line.grossAmount().quantity());
    }
  }

  private record SimulatorOrderResponse(
      @JsonProperty("psp_reference") String pspReference,
      @JsonProperty("payment_url") String paymentUrl) {}

  private record SimulatorRefundResponse(
      @JsonProperty("psp_refund_reference") String pspRefundReference, boolean accepted) {}
}
