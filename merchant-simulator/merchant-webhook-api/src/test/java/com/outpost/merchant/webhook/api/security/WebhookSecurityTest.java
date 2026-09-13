package com.outpost.merchant.webhook.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class WebhookSecurityTest {

  @Test
  void apiKeyFilterDoesNotApplyToUnrelatedPaths() throws Exception {
    ApiKeyAuthorizationFilter filter = new ApiKeyAuthorizationFilter("key");
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/unrelated");
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest()).isSameAs(request);
  }

  @Test
  void hmacFilterDoesNotApplyToUnrelatedPaths() throws Exception {
    HmacSignatureVerificationFilter filter =
        new HmacSignatureVerificationFilter(new HmacSignatureVerifier("secret"));
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/unrelated");
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest()).isSameAs(request);
  }

  @Test
  void blankWebhookConfigurationIsInvalid() {
    var validator = Validation.buildDefaultValidatorFactory().getValidator();

    assertThat(
            validator.validate(
                new com.outpost.merchant.webhook.configuration.WebhookProperties(null, " ")))
        .hasSize(2);
  }

  @Test
  void verifierUsesSeparateDigestForConcurrentCalls() throws Exception {
    String body = "body";
    String signature = signature("secret", body);
    HmacSignatureVerifier verifier = new HmacSignatureVerifier("secret");
    ExecutorService executor = Executors.newFixedThreadPool(8);

    try {
      var results =
          java.util.stream.IntStream.range(0, 200)
              .mapToObj(
                  ignored ->
                      executor.submit(
                          () -> verifier.isValid(signature, body.getBytes(StandardCharsets.UTF_8))))
              .toList();
      executor.shutdown();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
      assertThat(results).allMatch(future -> get(future));
    } finally {
      executor.shutdownNow();
    }
  }

  private static boolean get(java.util.concurrent.Future<Boolean> future) {
    try {
      return future.get();
    } catch (Exception exception) {
      throw new AssertionError(exception);
    }
  }

  private static String signature(String secret, String body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
  }
}
