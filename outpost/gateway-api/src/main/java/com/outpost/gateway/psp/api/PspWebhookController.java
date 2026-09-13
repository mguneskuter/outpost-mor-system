package com.outpost.gateway.psp.api;

import com.outpost.gateway.psp.service.PspWebhookProcessResultCodes;
import com.outpost.gateway.psp.service.PspWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Receives PSP event notifications that {@link PspWebhookSignatureFilter} authenticated. */
@RestController
public final class PspWebhookController {
  private final PspWebhookService service;

  /** Creates a webhook receiver. */
  public PspWebhookController(PspWebhookService service) {
    this.service = service;
  }

  /** Records a PSP event notification for its payment. */
  @PostMapping("/v1/psp/{pspCode}/webhook")
  ResponseEntity<PspWebhookEventResponse> process(
      @RequestAttribute(PspWebhookSignatureFilter.VERIFIED_WEBHOOK_ATTRIBUTE)
          VerifiedPspWebhook webhook,
      @RequestBody PspWebhookEventRequest request) {
    return PspWebhookResponses.respond(
        service.process(webhook.psp(), request.toEvent(), webhook.payload()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<PspWebhookEventResponse> unreadable(HttpMessageNotReadableException exception) {
    return PspWebhookResponses.respond(PspWebhookProcessResultCodes.INVALID_PAYLOAD);
  }
}
