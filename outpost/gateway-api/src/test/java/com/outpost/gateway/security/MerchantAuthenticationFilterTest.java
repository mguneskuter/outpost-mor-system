package com.outpost.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.outpost.account.configuration.MerchantApiKey;
import com.outpost.account.configuration.repository.MerchantApiKeyRepository;
import com.outpost.framework.security.encryption.AesGcmSecret;
import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.hmac.HmacSha256;
import com.outpost.framework.security.web.SizeBoundedRequestBody;
import com.outpost.gateway.DeclaredLengthRequest;
import com.outpost.gateway.api.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

class MerchantAuthenticationFilterTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  private static final String API_KEY = "demo-outpost-api-key";
  private static final String API_KEY_SHA256_HEX =
      "8e76b4677297200712e7f3e1348767a1fb76e1b43072209a2726e0057f8e36c6";
  private static final String INACTIVE_API_KEY_SHA256_HEX =
      "60688b29048f1487dd1bb57800856d8090f019366a950bd70e8ca114628a4009";
  private static final String HMAC_SECRET = "demo-hmac-secret";
  private static final String ENCRYPTED_HMAC_SECRET =
      "MTIzNDU2Nzg5MDEypG6klax6DJlGPy5yHDDXUn370yQJZ3t0WBCnZ/UkxYA=";
  private static final String ENCRYPTION_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
  private static final long ACCOUNT_ID = 42L;

  @Test
  void authenticatesSignedRequestForSeededMerchant() throws Exception {
    MerchantAuthenticationFilter filter =
        new MerchantAuthenticationFilter(
            apiKeyHash -> activeCredentialsFor(apiKeyHash), "", secrets(), JSON);
    MockHttpServletRequest request = signedRequest("payload");
    MockHttpServletResponse response = new MockHttpServletResponse();
    CapturingChain chain = new CapturingChain();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(chain.principal).isEqualTo(GatewayPrincipal.merchant(ACCOUNT_ID));
    assertThat(chain.body).isEqualTo("payload");
  }

  @ParameterizedTest
  @MethodSource("unauthenticatedRequests")
  void rejectsUnauthenticatedRequests(String scenario, MockHttpServletRequest request)
      throws Exception {
    MerchantAuthenticationFilter filter =
        new MerchantAuthenticationFilter(
            MerchantAuthenticationFilterTest::activeCredentialsFor, "", secrets(), JSON);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new CapturingChain());

    assertThat(response.getStatus()).as(scenario).isEqualTo(401);
    assertThat(response.getContentAsString())
        .as(scenario)
        .isEqualTo("{\"code\":\"UNAUTHENTICATED\"}");
  }

  @Test
  void authenticatesSignedBodyAtMaxSize() throws Exception {
    MerchantAuthenticationFilter filter =
        new MerchantAuthenticationFilter(
            MerchantAuthenticationFilterTest::activeCredentialsFor, "", secrets(), JSON);
    MockHttpServletResponse response = new MockHttpServletResponse();
    CapturingChain chain = new CapturingChain();

    filter.doFilter(
        signedRequest("a".repeat(SizeBoundedRequestBody.MAX_SIZE_BYTES)), response, chain);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(chain.principal).isEqualTo(GatewayPrincipal.merchant(ACCOUNT_ID));
    assertThat(chain.body).hasSize(SizeBoundedRequestBody.MAX_SIZE_BYTES);
  }

  @Test
  void rejectsBodyExceedingMaxSizeBeforeReadingItOrLookingUpCredentials() throws Exception {
    AtomicBoolean credentialsLookedUp = new AtomicBoolean();
    MerchantAuthenticationFilter filter =
        new MerchantAuthenticationFilter(
            apiKeyHash -> {
              credentialsLookedUp.set(true);
              return activeCredentialsFor(apiKeyHash);
            },
            "",
            secrets(),
            JSON);
    MockHttpServletRequest request =
        new DeclaredLengthRequest("/orders", SizeBoundedRequestBody.MAX_SIZE_BYTES + 1L);
    request.addHeader("X-Outpost-Api-Key", API_KEY);
    request.addHeader("X-Outpost-Signature", "c2lnbmF0dXJl");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new CapturingChain());

    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(response.getContentAsString()).isEqualTo("{\"code\":\"BODY_TOO_LARGE\"}");
    assertThat(credentialsLookedUp).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("unreadableCredentials")
  void answersServerErrorWithOneErrorLogWhenCredentialsCannotBeRead(
      String failure, MerchantApiKeyRepository merchantApiKeys, @Nullable String merchantAccountId)
      throws Exception {
    MerchantAuthenticationFilter filter =
        new MerchantAuthenticationFilter(merchantApiKeys, "", secrets(), JSON);
    MockHttpServletResponse response = new MockHttpServletResponse();
    Logger logger = (Logger) LoggerFactory.getLogger(MerchantAuthenticationFilter.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    try {
      filter.doFilter(signedRequest("payload"), response, new CapturingChain());

      assertThat(response.getStatus()).isEqualTo(500);
      assertThat(appender.list).hasSize(1);
      ILoggingEvent event = appender.list.getFirst();
      assertThat(event.getLevel()).isEqualTo(Level.ERROR);
      assertThat(event.getMDCPropertyMap())
          .containsEntry("authentication_failure", failure)
          .extractingByKey("merchant_account_id")
          .isEqualTo(merchantAccountId);
      ErrorResponse body = JSON.readValue(response.getContentAsString(), ErrorResponse.class);
      assertThat(body.code()).isEqualTo("INTERNAL_ERROR");
      assertThat(body.correlationId()).isEqualTo(event.getMDCPropertyMap().get("correlation_id"));
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void propagatesDownstreamIllegalArgumentException() {
    MerchantAuthenticationFilter filter =
        new MerchantAuthenticationFilter(
            MerchantAuthenticationFilterTest::activeCredentialsFor, "", secrets(), JSON);

    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            filter.doFilter(
                signedRequest("payload"),
                new MockHttpServletResponse(),
                (request, response) -> {
                  throw new IllegalArgumentException("downstream failure");
                }));
  }

  private static Stream<Arguments> unauthenticatedRequests() {
    MockHttpServletRequest wrongSignature = signedRequest("payload");
    wrongSignature.removeHeader("X-Outpost-Signature");
    wrongSignature.addHeader("X-Outpost-Signature", "wrong");
    MockHttpServletRequest absentSignature = request("payload");
    absentSignature.addHeader("X-Outpost-Api-Key", API_KEY);
    MockHttpServletRequest inactiveKey = signedRequest("payload");
    inactiveKey.removeHeader("X-Outpost-Api-Key");
    inactiveKey.addHeader("X-Outpost-Api-Key", "inactive-api-key");
    MockHttpServletRequest tamperedBody = signedRequest("payload");
    tamperedBody.setContent("tampered".getBytes(StandardCharsets.UTF_8));
    MockHttpServletRequest absentKey = request("payload");
    return Stream.of(
        Arguments.of("wrong signature", wrongSignature),
        Arguments.of("absent signature", absentSignature),
        Arguments.of("absent key", absentKey),
        Arguments.of("inactive key", inactiveKey),
        Arguments.of("tampered body", tamperedBody));
  }

  private static Stream<Arguments> unreadableCredentials() {
    MerchantApiKeyRepository unavailableStore =
        apiKeyHash -> {
          throw new IllegalStateException("credential store unavailable");
        };
    MerchantApiKeyRepository undecryptableSecret =
        apiKeyHash -> Optional.of(new MerchantApiKey(ACCOUNT_ID, "not-a-ciphertext"));
    return Stream.of(
        Arguments.of("CREDENTIAL_STORE", unavailableStore, null),
        Arguments.of("CREDENTIAL_DECRYPTION", undecryptableSecret, Long.toString(ACCOUNT_ID)));
  }

  private static MockHttpServletRequest signedRequest(String body) {
    MockHttpServletRequest request = request(body);
    request.addHeader("X-Outpost-Api-Key", API_KEY);
    request.addHeader(
        "X-Outpost-Signature",
        HmacSha256.sign(HmacKey.fromUtf8(HMAC_SECRET), body.getBytes(StandardCharsets.UTF_8))
            .toBase64());
    return request;
  }

  private static MockHttpServletRequest request(String body) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
    request.setContent(body.getBytes(StandardCharsets.UTF_8));
    return request;
  }

  private static MerchantApiKey credentials() {
    return new MerchantApiKey(ACCOUNT_ID, ENCRYPTED_HMAC_SECRET);
  }

  private static Optional<MerchantApiKey> activeCredentialsFor(String apiKeyHash) {
    if (apiKeyHash.equals(INACTIVE_API_KEY_SHA256_HEX)) {
      return Optional.empty();
    }
    assertThat(apiKeyHash).isEqualTo(API_KEY_SHA256_HEX);
    return Optional.of(credentials());
  }

  private static AesGcmSecret secrets() {
    return new AesGcmSecret(ENCRYPTION_KEY);
  }

  private static final class CapturingChain implements FilterChain {
    private @Nullable GatewayPrincipal principal;
    private @Nullable String body;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response)
        throws IOException, jakarta.servlet.ServletException {
      principal =
          (GatewayPrincipal) request.getAttribute(MerchantAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
      body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
