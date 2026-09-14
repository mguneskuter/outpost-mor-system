package com.outpost.gateway.psp.api;

import com.outpost.gateway.psp.service.PspWebhookResults;
import com.outpost.gateway.psp.service.PspWebhookService;
import java.util.regex.Pattern;
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
  /** The route a PSP posts its event notifications to. */
  public static final String WEBHOOK_PATH = "/v1/psp/{pspCode}/webhook";

  /** Matches a request URI on {@link #WEBHOOK_PATH}; the first group is the PSP code. */
  public static final Pattern WEBHOOK_PATH_PATTERN = Pattern.compile("/v1/psp/([^/]+)/webhook");

  private final PspWebhookService service;

  /** Creates a webhook receiver. */
  public PspWebhookController(PspWebhookService service) {
    this.service = service;
  }

  /** Queues a PSP event notification for the Ledger. */
  @PostMapping(WEBHOOK_PATH)
  ResponseEntity<PspWebhookEventResponse> receive(
      @RequestAttribute(PspWebhookSignatureFilter.SIGNED_REQUEST_ATTRIBUTE)
          SignedPspWebhookRequest signedRequest,
      @RequestBody PspWebhookEvent event) {
    return PspWebhookResponder.respond(
        service.queueEvent(signedRequest.psp(), event.toOrderEvent()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<PspWebhookEventResponse> unreadable(HttpMessageNotReadableException exception) {
    return PspWebhookResponder.respond(PspWebhookResults.INVALID_PAYLOAD);
  }
}
