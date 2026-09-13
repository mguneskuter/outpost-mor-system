package com.outpost.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.hmac.HmacSha256;
import com.outpost.gateway.security.AesGcmSecretAdapter;
import com.outpost.gateway.security.GatewayPrincipal;
import com.outpost.gateway.security.MerchantAuthenticationFilter;
import com.outpost.gateway.security.repository.MerchantApiKeyCredentials;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MerchantAuthenticationFilterTest {

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
            apiKeyHash -> activeCredentialsFor(apiKeyHash), "", secrets());
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
            MerchantAuthenticationFilterTest::activeCredentialsFor, "", secrets());
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new CapturingChain());

    assertThat(response.getStatus()).as(scenario).isEqualTo(401);
  }

  @Test
  void propagatesDownstreamIllegalArgumentException() {
    MerchantAuthenticationFilter filter =
        new MerchantAuthenticationFilter(
            MerchantAuthenticationFilterTest::activeCredentialsFor, "", secrets());

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

  private static MerchantApiKeyCredentials credentials() {
    return new MerchantApiKeyCredentials(ACCOUNT_ID, ENCRYPTED_HMAC_SECRET);
  }

  private static Optional<MerchantApiKeyCredentials> activeCredentialsFor(String apiKeyHash) {
    if (apiKeyHash.equals(INACTIVE_API_KEY_SHA256_HEX)) {
      return Optional.empty();
    }
    assertThat(apiKeyHash).isEqualTo(API_KEY_SHA256_HEX);
    return Optional.of(credentials());
  }

  private static AesGcmSecretAdapter secrets() {
    return new AesGcmSecretAdapter(ENCRYPTION_KEY);
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
