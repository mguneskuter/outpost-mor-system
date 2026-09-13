package com.outpost.worker.accounting.client.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.accounting.TransactionEventTypes;
import com.outpost.accounting.api.CaptureRequest;
import com.outpost.accounting.api.CaptureResponse;
import com.outpost.accounting.api.CreatePaymentRequest;
import com.outpost.accounting.api.PaymentApi;
import com.outpost.accounting.api.PaymentEventRequest;
import com.outpost.accounting.api.PaymentResponse;
import com.outpost.accounting.api.RefundRequest;
import com.outpost.accounting.api.RefundResponse;
import com.outpost.worker.accounting.client.LedgerPaymentClientException;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

class LedgerPaymentHttpClientTest {
  private final FakePaymentApi ledger = new FakePaymentApi();
  private final LedgerPaymentHttpClient client = new LedgerPaymentHttpClient(ledger);

  @Test
  void sendsRefundEventWithItsWireCode() {
    client.appendPaymentEvent(
        "payment-1", "refund-1", TransactionEventTypes.REFUND_ACCEPTED.getValue());

    assertThat(ledger.requests)
        .containsExactly(new PaymentEventRequest("payment-1", "refund-1", "REFUND_ACCEPTED"));
  }

  @Test
  void sendsPaymentEventWithoutRefundReference() {
    client.appendPaymentEvent("payment-1", null, TransactionEventTypes.AUTHORISED.getValue());

    assertThat(ledger.requests)
        .containsExactly(new PaymentEventRequest("payment-1", null, "AUTHORISED"));
  }

  @Test
  void sendsCapture() {
    client.recordCapture("payment-1", "capture-1", true, 1250, "EUR");

    assertThat(ledger.requests)
        .containsExactly(new CaptureRequest("payment-1", "capture-1", true, 1250L, "EUR"));
  }

  @Test
  void sendsRefundReservation() {
    client.reserveRefund("payment-1", "refund-1", 800, 152, "EUR");

    assertThat(ledger.requests)
        .containsExactly(new RefundRequest("payment-1", "refund-1", 800L, 152L, "EUR"));
  }

  @Test
  void wrapsLedgerRejection() {
    ledger.failure = new HttpClientErrorException(HttpStatus.CONFLICT);

    assertThatThrownBy(
            () ->
                client.appendPaymentEvent(
                    "payment-1", null, TransactionEventTypes.AUTHORISED.getValue()))
        .isInstanceOf(LedgerPaymentClientException.class);
  }

  private static final class FakePaymentApi implements PaymentApi {
    private final List<Object> requests = new ArrayList<>();
    private @Nullable RuntimeException failure;

    @Override
    public ResponseEntity<PaymentResponse> create(CreatePaymentRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ResponseEntity<Void> appendPaymentEvent(PaymentEventRequest request) {
      accept(request);
      return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<CaptureResponse> capture(CaptureRequest request) {
      accept(request);
      return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Override
    public ResponseEntity<RefundResponse> refund(RefundRequest request) {
      accept(request);
      return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private void accept(Object request) {
      if (failure != null) {
        throw failure;
      }
      requests.add(request);
    }
  }
}
