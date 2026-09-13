package com.outpost.ledger.payment.api;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** HMAC credentials accepted by Ledger service routes. */
@Validated
@ConfigurationProperties("outpost.ledger")
public record LedgerAuthenticationProperties(
    @NotBlank(message = "must be set") String gatewayHmacSecret,
    @NotBlank(message = "must be set") String workerHmacSecret) {}
