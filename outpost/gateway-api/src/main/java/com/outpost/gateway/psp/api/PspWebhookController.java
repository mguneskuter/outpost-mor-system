package com.outpost.gateway.psp.api;

import com.outpost.gateway.psp.service.PspWebhookService;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Receives signed PSP event notifications. */
@RestController
public final class PspWebhookController {
  private final PspWebhookService service;

  /** Creates a webhook receiver. */
  public PspWebhookController(PspWebhookService service) {
    this.service = service;
  }

  /** Verifies and stores a PSP event notification. */
  @PostMapping("/v1/psp/{pspCode}/webhook")
  public ResponseEntity<Void> receive(
      @PathVariable String pspCode,
      @RequestHeader(name = "X-Outpost-Signature", required = false) @Nullable String signature,
      @RequestBody byte[] body) {
    return switch (service.receive(pspCode, signature, body)) {
      case RECORDED -> ResponseEntity.ok().build();
      case UNKNOWN_PSP -> ResponseEntity.notFound().build();
      case INVALID_SIGNATURE -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
      case INVALID_PAYLOAD -> ResponseEntity.badRequest().build();
      case UNKNOWN_OR_FOREIGN_PAYMENT -> ResponseEntity.unprocessableContent().build();
    };
  }
}
