package com.outpost.integration.psp.simulator;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.outpost.integration.psp.service.CreateOrderRequest;
import com.outpost.integration.psp.service.CreateOrderResult;
import com.outpost.integration.psp.service.PspClient;
import com.outpost.integration.psp.service.RefundRequest;
import com.outpost.integration.psp.service.RefundResult;
import com.outpost.integration.psp.service.ResultCode;
import com.outpost.integration.psp.simulator.repository.PspConfiguration;
import com.outpost.integration.psp.simulator.repository.PspConfigurationRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Calls the simulator protocol and translates transport failures into outcomes. */
public final class SimulatorPspClient implements PspClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(SimulatorPspClient.class);
  private final PspConfigurationRepository configurations;

  /** Creates a client backed by the supplied configuration lookup. */
  public SimulatorPspClient(PspConfigurationRepository configurations) {
    this.configurations = configurations;
  }

  /** Sends an order request. */
  @Override
  public CreateOrderResult createOrder(CreateOrderRequest request) {
    return configurations
        .findByCode(request.pspCode())
        .map(c -> createOrderCall(c, request))
        .orElse(new CreateOrderResult("", "", ResultCode.REJECTED));
  }

  /** Sends a refund request. */
  @Override
  public RefundResult refund(RefundRequest request) {
    return configurations
        .findByCode(request.pspCode())
        .map(c -> refundCall(c, request))
        .orElse(new RefundResult("", "", ResultCode.REJECTED));
  }

  private CreateOrderResult createOrderCall(
      PspConfiguration configuration, CreateOrderRequest request) {
    try {
      SimulatorOrderResponse response =
          post(configuration, "/order", OrderRequest.from(request), SimulatorOrderResponse.class);
      return response == null
          ? new CreateOrderResult("", "", ResultCode.REJECTED)
          : new CreateOrderResult(
              response.pspReference(), response.paymentUrl(), ResultCode.ACCEPTED);
    } catch (SimulatorTimeoutException ex) {
      return new CreateOrderResult("", "", ResultCode.UNKNOWN);
    }
  }

  private RefundResult refundCall(PspConfiguration configuration, RefundRequest request) {
    try {
      SimulatorRefundResponse response =
          post(
              configuration,
              "/refund",
              SimulatorRefundRequest.from(request),
              SimulatorRefundResponse.class);
      return response == null
          ? new RefundResult("", "", ResultCode.REJECTED)
          : new RefundResult(
              request.pspReference(),
              response.pspRefundReference(),
              response.accepted() ? ResultCode.ACCEPTED : ResultCode.REJECTED);
    } catch (SimulatorTimeoutException ex) {
      return new RefundResult(request.pspReference(), "", ResultCode.UNKNOWN);
    }
  }

  @Nullable
  private <T> T post(PspConfiguration configuration, String route, Object body, Class<T> type) {
    try {
      return java.util.Objects.requireNonNull(
          client(configuration)
              .post()
              .uri(URI.create("/v1/" + configuration.code() + route))
              .header("X-Outpost-Api-Key", configuration.apiKey())
              .body(body)
              .retrieve()
              .body(type));
    } catch (RuntimeException ex) {
      if (isTimeout(ex)) {
        LOGGER.warn("PSP call timed out pspCode={} route={}", configuration.code(), route, ex);
        throw new SimulatorTimeoutException();
      }
      LOGGER.warn("PSP call failed pspCode={} route={}", configuration.code(), route, ex);
      return null;
    }
  }

  private RestClient client(PspConfiguration configuration) {
    return RestClient.builder()
        .requestFactory(newRequestFactory(configuration))
        .baseUrl(configuration.baseUrl())
        .build();
  }

  private static boolean isTimeout(RuntimeException exception) {
    return exception.getCause() instanceof java.io.IOException;
  }

  private static final class SimulatorTimeoutException extends RuntimeException {}

  private record OrderRequest(
      @JsonProperty("payment_reference") String paymentReference, long amount, String currency) {
    static OrderRequest from(CreateOrderRequest request) {
      return new OrderRequest(
          request.paymentReference(),
          request.amount().quantity(),
          request.amount().currency().getCurrencyCode());
    }
  }

  private record SimulatorRefundRequest(
      @JsonProperty("psp_reference") String pspReference,
      @JsonProperty("refund_reference") String refundReference) {
    static SimulatorRefundRequest from(RefundRequest request) {
      return new SimulatorRefundRequest(request.pspReference(), request.refundReference());
    }
  }

  private record SimulatorOrderResponse(
      @JsonProperty("psp_reference") String pspReference,
      @JsonProperty("payment_url") String paymentUrl) {}

  private record SimulatorRefundResponse(
      @JsonProperty("psp_refund_reference") String pspRefundReference, boolean accepted) {}

  private static JdkClientHttpRequestFactory newRequestFactory(PspConfiguration c) {
    HttpClient http =
        HttpClient.newBuilder().connectTimeout(Duration.ofMillis(c.connectTimeoutMillis())).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(Duration.ofMillis(c.readTimeoutMillis()));
    return factory;
  }
}
