package com.outpost.gateway.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Keys Gateway authenticates callers with.
 *
 * @param encryptionKey Base64 AES key that decrypts stored merchant HMAC secrets
 * @param operatorApiKey the operator's API key; blank means no operator can authenticate
 */
@Validated
@ConfigurationProperties("outpost.gateway.authentication")
public record GatewayAuthenticationProperties(
    @NotBlank(message = "must be set") String encryptionKey,
    @NotNull(message = "must be set") String operatorApiKey) {}
