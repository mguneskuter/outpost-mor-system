package com.outpost.framework.security.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.hmac.HmacSha256;
import jakarta.servlet.ServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class SignatureAuthenticationFilterTest {
  private static final HmacKey ORDERS_KEY = HmacKey.fromUtf8("orders-secret");
  private static final HmacKey BILLING_KEY = HmacKey.fromUtf8("billing-secret");
  private static final String BODY = "{\"amount\":100}";

  private final SignatureAuthenticationFilter filter =
      new SignatureAuthenticationFilter(Map.of("ORDERS", ORDERS_KEY, "BILLING", BILLING_KEY));

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void authenticatesTheCallerWhoseKeyVerifiesTheSignature() throws Exception {
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(
        request(HmacSha256.signUtf8(BILLING_KEY, BODY).toBase64()),
        new MockHttpServletResponse(),
        chain);

    Authentication authentication =
        Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication());
    assertThat(authentication.getName()).isEqualTo("BILLING");
    assertThat(authentication.getAuthorities())
        .extracting(authority -> authority.getAuthority())
        .containsExactly("BILLING");
    ServletRequest forwarded = Objects.requireNonNull(chain.getRequest());
    assertThat(new String(forwarded.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
        .isEqualTo(BODY);
  }

  @Test
  void leavesRequestSignedWithUnknownKeyUnauthenticated() throws Exception {
    MockFilterChain chain = new MockFilterChain();
    String unknownSignature =
        HmacSha256.signUtf8(HmacKey.fromUtf8("unknown-secret"), BODY).toBase64();

    filter.doFilter(request(unknownSignature), new MockHttpServletResponse(), chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void leavesMalformedSignatureUnauthenticated() throws Exception {
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request("not-base64!"), new MockHttpServletResponse(), chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void refusesCallersThatShareKey() {
    Map<String, HmacKey> keysByCaller =
        Map.of("ORDERS", HmacKey.fromUtf8("shared"), "BILLING", HmacKey.fromUtf8("shared"));

    assertThatThrownBy(() -> new SignatureAuthenticationFilter(keysByCaller))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static MockHttpServletRequest request(String signature) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/resource");
    request.setContent(BODY.getBytes(StandardCharsets.UTF_8));
    request.addHeader("X-Outpost-Signature", signature);
    return request;
  }
}
