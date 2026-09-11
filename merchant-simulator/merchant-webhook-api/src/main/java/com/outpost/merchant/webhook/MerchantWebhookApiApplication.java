package com.outpost.merchant.webhook;

import com.outpost.merchant.webhook.api.IncomingWebhookController;
import com.outpost.merchant.webhook.api.security.ApiKeyAuthorizationFilter;
import com.outpost.merchant.webhook.api.security.HmacSignatureVerificationFilter;
import com.outpost.merchant.webhook.api.security.HmacSignatureVerifier;
import com.outpost.merchant.webhook.configuration.WebhookProperties;
import java.util.Objects;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/** The merchant's webhook receiver accepts payment callbacks from Outpost. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MerchantWebhookApiApplication {

  /** Starts the application. */
  public static void main(String[] args) {
    SpringApplication.run(MerchantWebhookApiApplication.class, args);
  }

  @Bean
  HmacSignatureVerifier hmacSignatureVerifier(WebhookProperties properties) {
    return new HmacSignatureVerifier(Objects.requireNonNull(properties.secret()));
  }

  @Bean
  FilterRegistrationBean<ApiKeyAuthorizationFilter> apiKeyAuthorizationFilter(
      WebhookProperties properties) {
    FilterRegistrationBean<ApiKeyAuthorizationFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(
        new ApiKeyAuthorizationFilter(Objects.requireNonNull(properties.apiKey())));
    registration.addUrlPatterns(IncomingWebhookController.WEBHOOK_EVENTS_PATH);
    return registration;
  }

  @Bean
  FilterRegistrationBean<HmacSignatureVerificationFilter> hmacSignatureVerificationFilter(
      HmacSignatureVerifier verifier) {
    FilterRegistrationBean<HmacSignatureVerificationFilter> registration =
        new FilterRegistrationBean<>();
    registration.setFilter(new HmacSignatureVerificationFilter(verifier));
    registration.addUrlPatterns(IncomingWebhookController.WEBHOOK_EVENTS_PATH);
    return registration;
  }
}
