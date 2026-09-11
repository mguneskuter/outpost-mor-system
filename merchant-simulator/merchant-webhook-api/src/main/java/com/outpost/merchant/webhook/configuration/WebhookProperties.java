package com.outpost.merchant.webhook.configuration;

import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configuration required to authenticate incoming webhooks. */
@ConfigurationProperties("merchant.webhook")
@Validated
public record WebhookProperties(
    @NotBlank @Nullable String secret, @NotBlank @Nullable String apiKey) {}
