package com.outpost.gateway.psp.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.hmac.HmacSha256;
import com.outpost.framework.security.hmac.HmacSignature;
import com.outpost.integration.psp.simulator.repository.PspConfiguration;
import com.outpost.integration.psp.simulator.repository.PspConfigurationRepository;
import com.outpost.payment.PspEventCodes;
import com.outpost.payment.repository.PspEventQueue;
import com.outpost.payment.repository.PspEventQueue.PaymentAccounts;
import com.outpost.payment.repository.PspEventQueue.ReceivedPspEvent;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Verifies PSP event notifications before recording them. */
public final class PspWebhookService {
  private final PspConfigurationRepository configurations;
  private final PspEventQueue events;
  private final ObjectMapper objectMapper;

  /** Creates a webhook intake service. */
  public PspWebhookService(
      PspConfigurationRepository configurations, PspEventQueue events, ObjectMapper objectMapper) {
    this.configurations = configurations;
    this.events = events;
    this.objectMapper = objectMapper;
  }

  /** Verifies and records one PSP event notification. */
  public PspWebhookIntakeResults receive(String pathCode, @Nullable String signature, byte[] body) {
    Optional<PspConfiguration> configuration = configurations.findByCode(pathCode);
    if (configuration.isEmpty()) {
      return PspWebhookIntakeResults.UNKNOWN_PSP;
    }
    PspConfiguration configuredPsp = configuration.orElseThrow();
    if (!hasValidSignature(configuredPsp, signature, body)) {
      return PspWebhookIntakeResults.INVALID_SIGNATURE;
    }
    Optional<PspWebhook> webhook = parse(body);
    if (webhook.isEmpty() || !configuredPsp.code().equals(webhook.orElseThrow().pspCode())) {
      return PspWebhookIntakeResults.INVALID_PAYLOAD;
    }
    PspWebhook receivedWebhook = webhook.orElseThrow();
    Optional<PaymentAccounts> accounts =
        events
            .findPaymentAccounts(receivedWebhook.paymentReference())
            .filter(value -> value.pspAccountId() == configuredPsp.accountId());
    if (accounts.isEmpty()) {
      return PspWebhookIntakeResults.UNKNOWN_OR_FOREIGN_PAYMENT;
    }
    PaymentAccounts paymentAccounts = accounts.orElseThrow();
    events.recordReceived(
        new ReceivedPspEvent(
            paymentAccounts.merchantAccountId(),
            paymentAccounts.pspAccountId(),
            receivedWebhook.eventReference(),
            receivedWebhook.paymentReference(),
            receivedWebhook.eventCode(),
            new String(body, StandardCharsets.UTF_8)));
    return PspWebhookIntakeResults.RECORDED;
  }

  private static boolean hasValidSignature(
      PspConfiguration configuration, @Nullable String signature, byte[] body) {
    try {
      if (signature == null || signature.isBlank()) {
        return false;
      }
      return HmacSha256.verify(
          HmacKey.fromUtf8(configuration.hmacSecret()), body, HmacSignature.fromBase64(signature));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private Optional<PspWebhook> parse(byte[] body) {
    try {
      return Optional.ofNullable(objectMapper.readValue(body, PspWebhook.class));
    } catch (JacksonException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  @SuppressWarnings("UnusedMethod")
  private record PspWebhook(
      @JsonProperty("psp_code") String pspCode,
      @JsonProperty("psp_reference") String pspReference,
      @JsonProperty("psp_refund_reference") @Nullable String pspRefundReference,
      @JsonProperty("payment_reference") String paymentReference,
      @JsonProperty("event_code") PspEventCodes eventCode,
      long timestamp,
      boolean success,
      @JsonProperty("result_code") String resultCode,
      long amount,
      String currency,
      @JsonProperty("refund_reference") @Nullable String refundReference) {
    PspWebhook {
      Objects.requireNonNull(pspCode, "pspCode");
      Objects.requireNonNull(pspReference, "pspReference");
      Objects.requireNonNull(paymentReference, "paymentReference");
      Objects.requireNonNull(eventCode, "eventCode");
      Objects.requireNonNull(resultCode, "resultCode");
      Objects.requireNonNull(currency, "currency");
      if (eventCode == PspEventCodes.REFUND) {
        Objects.requireNonNull(pspRefundReference, "pspRefundReference");
      }
    }

    String eventReference() {
      if (eventCode == PspEventCodes.REFUND) {
        return Objects.requireNonNull(pspRefundReference, "pspRefundReference");
      }
      return pspReference;
    }
  }
}
