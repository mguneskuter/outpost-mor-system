package com.outpost.merchant.webhook.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Accepts typed webhook callbacks from Outpost. */
@RestController
public final class IncomingWebhookController {
  public static final String WEBHOOK_EVENTS_PATH = "/v1/webhook/events";

  private static final Logger log = LoggerFactory.getLogger(IncomingWebhookController.class);

  @PostMapping(WEBHOOK_EVENTS_PATH)
  IncomingWebhookResponse receive(@RequestBody IncomingWebhookRequest request) {
    log.info(
        "Accepted Outpost webhook: merchantReference={}, outpostReference={}, eventType={}, "
            + "eventTimestamp={}, success={}, reason={}",
        request.merchantReference(),
        request.outpostReference(),
        request.eventType(),
        request.eventTimestamp(),
        request.success(),
        request.reason());
    return new IncomingWebhookResponse(
        request.success(),
        request.merchantReference(),
        request.outpostReference(),
        request.eventType(),
        request.eventTimestamp(),
        request.reason());
  }
}
