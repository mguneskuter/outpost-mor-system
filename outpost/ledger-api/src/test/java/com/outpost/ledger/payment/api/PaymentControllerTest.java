package com.outpost.ledger.payment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.outpost.accounting.api.RefundRequest;
import com.outpost.accounting.api.RefundResponse;
import com.outpost.ledger.payment.service.CaptureService;
import com.outpost.ledger.payment.service.PaymentCreationService;
import com.outpost.ledger.payment.service.PaymentEventService;
import com.outpost.ledger.payment.service.RefundReservationService;
import com.outpost.ledger.payment.service.ReserveRefundCommand;
import com.outpost.ledger.payment.service.ReserveRefundResult;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class PaymentControllerTest {
  @Test
  void mapsRefundPayloadToCommandAndResultToResponse() {
    PaymentCreationService paymentCreationService = mock(PaymentCreationService.class);
    PaymentEventService paymentEventService = mock(PaymentEventService.class);
    CaptureService captureService = mock(CaptureService.class);
    RefundReservationService refundReservationService = mock(RefundReservationService.class);
    PaymentController controller =
        new PaymentController(
            paymentCreationService, paymentEventService, captureService, refundReservationService);
    RefundRequest request = new RefundRequest("payment-1", "refund-1", 8000L, 2000L, "EUR");
    Instant createdAt = Instant.parse("2026-09-12T00:00:00Z");
    ReserveRefundCommand command =
        new ReserveRefundCommand("payment-1", "refund-1", 8000L, 2000L, "EUR");
    when(refundReservationService.reserve(command))
        .thenReturn(new ReserveRefundResult("refund-1", createdAt));

    ResponseEntity<RefundResponse> response = controller.refund(request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isEqualTo(new RefundResponse("refund-1", createdAt));
    verify(refundReservationService).reserve(command);
  }
}
