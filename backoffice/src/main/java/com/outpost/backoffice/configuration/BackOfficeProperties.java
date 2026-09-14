package com.outpost.backoffice.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * What the back office needs to act as a merchant: where the Gateway is, the credentials of each
 * merchant it may act as, the operator's key for the platform report, the PSP simulator's key for
 * paying, where the back office itself reaches the PSP when that differs from the payment links the
 * PSP publishes for shoppers, and the merchant's product catalogue.
 *
 * @param pspBaseUrl the base URL the back office pays at in place of each payment link's own, or
 *     null to pay at the payment link as published
 */
@Validated
@ConfigurationProperties("backoffice")
public record BackOfficeProperties(
    URI gatewayBaseUrl,
    @NotBlank String operatorApiKey,
    @NotBlank String pspApiKey,
    @Nullable URI pspBaseUrl,
    @NotEmpty Map<String, @Valid MerchantCredentials> merchants,
    @NotEmpty List<@Valid CatalogueItem> catalogue) {
  /** Copies the maps and lists so the properties stay immutable. */
  public BackOfficeProperties {
    merchants = Map.copyOf(merchants);
    catalogue = List.copyOf(catalogue);
  }

  /** One merchant's Gateway credentials, keyed by its account code. */
  public record MerchantCredentials(@NotBlank String apiKey, @NotBlank String hmacSecret) {}

  /** One thing the merchant sells: a net price in one currency and its goods type. */
  public record CatalogueItem(
      @NotBlank String sku,
      @NotBlank String name,
      @Positive long amount,
      @NotBlank String currency,
      @NotBlank String type) {}
}
