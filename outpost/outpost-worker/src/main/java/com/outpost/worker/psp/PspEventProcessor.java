package com.outpost.worker.psp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.outpost.accounting.queue.AccountingRequestQueue;
import com.outpost.accounting.queue.AccountingRequestTypes;
import com.outpost.accounting.queue.SubmitAccountingRequestCommand;
import com.outpost.common.iso.Currencies;
import com.outpost.framework.logging.LogFields;
import com.outpost.framework.logging.StructuredLogField;
import com.outpost.framework.logging.StructuredLogger;
import com.outpost.payment.PspEventCodes;
import com.outpost.payment.PspEventResults;
import com.outpost.payment.repository.PspEventRepository;
import com.outpost.payment.repository.PspEventRepository.PspEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Turns claimed PSP events into accounting requests. */
public final class PspEventProcessor {
  private static final StructuredLogger LOGGER =
      new StructuredLogger(LoggerFactory.getLogger(PspEventProcessor.class));
  private final PspEventRepository events;
  private final AccountingRequestQueue requests;
  private final ObjectMapper objectMapper;
  private final TransactionTemplate transactions;
  private final Clock clock;

  /** Creates a processor that commits an event outcome with its accounting request. */
  public PspEventProcessor(
      PspEventRepository events,
      AccountingRequestQueue requests,
      ObjectMapper objectMapper,
      TransactionTemplate transactions,
      Clock clock) {
    this.events = events;
    this.requests = requests;
    this.objectMapper = objectMapper;
    this.transactions = transactions;
    this.clock = clock;
  }

  /** Processes one eligible event when one is available. */
  public boolean processNext() {
    return processNext(PspEventRepository::claimNext);
  }

  boolean processNext(PspEventClaimer claimer) {
    Boolean processed =
        transactions.execute(
            ignored -> {
              PspEvent event = claimer.claimNext(events).orElse(null);
              if (event == null) {
                return false;
              }
              try {
                requests.submit(toCommand(event));
                events.complete(event.queueId(), PspEventResults.SUCCESS, now());
              } catch (PspEventMappingException | IllegalArgumentException exception) {
                LOGGER.warn(
                    "PSP event could not be mapped",
                    exception,
                    new StructuredLogField(LogField.QUEUE_ID, Long.toString(event.queueId())),
                    new StructuredLogField(LogField.PAYMENT_REFERENCE, event.originalReference()));
                events.complete(event.queueId(), PspEventResults.FAILED, now());
              }
              return true;
            });
    return Boolean.TRUE.equals(processed);
  }

  @FunctionalInterface
  interface PspEventClaimer {
    Optional<PspEvent> claimNext(PspEventRepository events);
  }

  private SubmitAccountingRequestCommand toCommand(PspEvent event) {
    PspEventPayload payload = parse(event.payload());
    AccountingRequestTypes type = requestType(event.eventCode());
    String reference = reference(event, payload);
    Long amount = event.eventCode() == PspEventCodes.CAPTURE ? payload.amount() : null;
    Long currencyId =
        event.eventCode() == PspEventCodes.CAPTURE ? currencyId(payload.currency()) : null;
    return new SubmitAccountingRequestCommand(
        type,
        reference,
        event.originalReference(),
        event.merchantAccountId(),
        event.queueId(),
        null,
        null,
        payload.success(),
        amount,
        currencyId,
        payload.pspReference(),
        List.of());
  }

  private PspEventPayload parse(String payload) {
    try {
      return objectMapper.readValue(payload, PspEventPayload.class);
    } catch (JacksonException | IllegalArgumentException exception) {
      throw new PspEventMappingException(exception);
    }
  }

  private static AccountingRequestTypes requestType(PspEventCodes eventCode) {
    return switch (eventCode) {
      case AUTHORISATION -> AccountingRequestTypes.AUTHORISATION_RESULT;
      case CAPTURE -> AccountingRequestTypes.CAPTURE_RESULT;
      case CANCELLATION -> AccountingRequestTypes.CANCELLATION_RESULT;
      case REFUND -> AccountingRequestTypes.REFUND_RESULT;
    };
  }

  private static String reference(PspEvent event, PspEventPayload payload) {
    return switch (event.eventCode()) {
      case AUTHORISATION, CANCELLATION -> event.originalReference();
      case CAPTURE -> UUID.randomUUID().toString();
      case REFUND -> {
        String refundReference = payload.refundReference();
        if (refundReference == null || refundReference.isBlank()) {
          throw new PspEventMappingException("Missing refund reference");
        }
        yield refundReference;
      }
    };
  }

  private static long currencyId(String currency) {
    return Currencies.fromCurrencyCode(currency)
        .orElseThrow(() -> new PspEventMappingException("Unsupported currency"))
        .getCurrencyId();
  }

  private Instant now() {
    return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
  }

  private record PspEventPayload(
      @JsonProperty("psp_reference") String pspReference,
      boolean success,
      long amount,
      String currency,
      @JsonProperty("refund_reference") String refundReference) {
    // Invoked by Jackson through reflection to deserialize the PSP event payload; no call
    // site in this class reaches it directly.
    @SuppressWarnings("UnusedMethod")
    PspEventPayload {
      Objects.requireNonNull(pspReference, "pspReference");
      Objects.requireNonNull(currency, "currency");
      if (amount < 0) {
        throw new IllegalArgumentException("amount must not be negative");
      }
    }
  }

  private static final class PspEventMappingException extends RuntimeException {
    PspEventMappingException(Exception cause) {
      super(cause);
    }

    PspEventMappingException(String message) {
      super(message);
    }
  }

  private enum LogField implements LogFields {
    PAYMENT_REFERENCE("payment_reference"),
    QUEUE_ID("queue_id");

    private final String jsonKey;

    LogField(String jsonKey) {
      this.jsonKey = jsonKey;
    }

    @Override
    public String getJsonKey() {
      return jsonKey;
    }
  }
}
