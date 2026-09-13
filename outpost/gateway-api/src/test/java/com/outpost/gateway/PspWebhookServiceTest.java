package com.outpost.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.gateway.psp.service.PspWebhookEvent;
import com.outpost.gateway.psp.service.PspWebhookProcessResultCodes;
import com.outpost.gateway.psp.service.PspWebhookService;
import com.outpost.integration.psp.simulator.repository.PspConfiguration;
import com.outpost.payment.PspEventCodes;
import com.outpost.payment.PspEventResults;
import com.outpost.payment.repository.PspEventRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PspWebhookServiceTest {
  private static final long MERCHANT_ACCOUNT_ID = 10L;
  private static final long PSP_ACCOUNT_ID = 20L;
  private static final long OTHER_PSP_ACCOUNT_ID = 30L;
  private static final String PAYMENT_REFERENCE = "payment-1";
  private static final String PSP_REFERENCE = "psp-payment-1";
  private static final String PAYLOAD = "{\"signed\":true}";
  private static final PspConfiguration PSP =
      new PspConfiguration(
          PSP_ACCOUNT_ID, "PSP", "https://psp.example.test", "key", "secret", 1, 1);

  private final RecordingPspEventRepository events = new RecordingPspEventRepository();
  private final PspWebhookService service = new PspWebhookService(events);

  @Test
  void refusesSignedEventWhosePspReferenceDiffersFromTheStoredOne() {
    events.storePayment(PAYMENT_REFERENCE, PSP_ACCOUNT_ID, PSP_REFERENCE);

    PspWebhookProcessResultCodes result =
        service.process(PSP, event(PspEventCodes.CAPTURE, "psp-payment-other"), PAYLOAD);

    assertThat(result).isEqualTo(PspWebhookProcessResultCodes.PSP_REFERENCE_MISMATCH);
    assertThat(events.recorded).isEmpty();
  }

  @Test
  void refusesSignedEventForPaymentWithoutStoredPspReference() {
    events.storePayment(PAYMENT_REFERENCE, PSP_ACCOUNT_ID, null);

    PspWebhookProcessResultCodes result =
        service.process(PSP, event(PspEventCodes.AUTHORISATION, PSP_REFERENCE), PAYLOAD);

    assertThat(result).isEqualTo(PspWebhookProcessResultCodes.PSP_REFERENCE_MISMATCH);
    assertThat(events.recorded).isEmpty();
  }

  @Test
  void refusesEventForPaymentOfAnotherPspAccountDistinctlyFromReferenceMismatch() {
    events.storePayment(PAYMENT_REFERENCE, OTHER_PSP_ACCOUNT_ID, PSP_REFERENCE);

    PspWebhookProcessResultCodes result =
        service.process(PSP, event(PspEventCodes.CAPTURE, PSP_REFERENCE), PAYLOAD);

    assertThat(result).isEqualTo(PspWebhookProcessResultCodes.FOREIGN_PAYMENT);
    assertThat(events.recorded).isEmpty();
  }

  @Test
  void refusesEventForUnknownPayment() {
    PspWebhookProcessResultCodes result =
        service.process(PSP, event(PspEventCodes.CAPTURE, PSP_REFERENCE), PAYLOAD);

    assertThat(result).isEqualTo(PspWebhookProcessResultCodes.UNKNOWN_PAYMENT);
    assertThat(events.recorded).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(PspEventCodes.class)
  void recordsEventWhosePspAccountAndReferenceMatchTheStoredPayment(PspEventCodes eventCode) {
    events.storePayment(PAYMENT_REFERENCE, PSP_ACCOUNT_ID, PSP_REFERENCE);
    PspWebhookEvent event = event(eventCode, PSP_REFERENCE);

    PspWebhookProcessResultCodes result = service.process(PSP, event, PAYLOAD);

    assertThat(result).isEqualTo(PspWebhookProcessResultCodes.ACCEPTED);
    assertThat(events.recorded)
        .containsExactly(
            new PspEventRepository.ReceivedPspEvent(
                MERCHANT_ACCOUNT_ID,
                PSP_ACCOUNT_ID,
                event.eventReference(),
                PAYMENT_REFERENCE,
                eventCode,
                PAYLOAD));
  }

  private static PspWebhookEvent event(PspEventCodes eventCode, String pspReference) {
    return new PspWebhookEvent(
        PSP.code(), pspReference, PAYMENT_REFERENCE, eventCode, eventCode + "-event");
  }

  private static final class RecordingPspEventRepository implements PspEventRepository {
    private final Map<String, PaymentAccounts> payments = new HashMap<>();
    private final List<ReceivedPspEvent> recorded = new ArrayList<>();

    void storePayment(String paymentReference, long pspAccountId, @Nullable String pspReference) {
      payments.put(
          paymentReference, new PaymentAccounts(MERCHANT_ACCOUNT_ID, pspAccountId, pspReference));
    }

    @Override
    public Optional<PaymentAccounts> findPaymentAccounts(String paymentReference) {
      return Optional.ofNullable(payments.get(paymentReference));
    }

    @Override
    public void recordReceived(ReceivedPspEvent event) {
      recorded.add(event);
    }

    @Override
    public Optional<PspEvent> claimNext() {
      return Optional.empty();
    }

    @Override
    public void complete(long queueId, PspEventResults result) {}
  }
}
