package com.outpost.ledger.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** HMAC secret with which Gateway signs its Ledger requests. */
@Validated
@ConfigurationProperties("outpost.ledger")
public record LedgerAuthenticationProperties(
    @NotBlank(message = "must be set") String gatewayHmacSecret) {}
