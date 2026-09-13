package com.outpost.framework.security.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outpost.framework.security.hmac.HmacKey;
import com.outpost.framework.security.hmac.HmacSha256;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.DelegatingServletInputStream;
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
      new SignatureAuthenticationFilter(
          Map.of("ORDERS", ORDERS_KEY, "BILLING", BILLING_KEY),
          SignatureAuthenticationFilterTest::writeErrorBody);

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
  void authenticatesSignedBodyAtMaxSize() throws Exception {
    byte[] body = new byte[SizeBoundedRequestBody.MAX_SIZE_BYTES];
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/resource");
    request.setContent(body);
    request.addHeader("X-Outpost-Signature", HmacSha256.sign(ORDERS_KEY, body).toBase64());
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(SecurityContextHolder.getContext().getAuthentication())
        .extracting(Authentication::getName)
        .isEqualTo("ORDERS");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("bodiesExceededMaxSize")
  void rejectsSignedBodyExceedingMaxSizeWithoutAuthenticating(
      String scenario, MockHttpServletRequest request) throws Exception {
    request.addHeader("X-Outpost-Signature", HmacSha256.signUtf8(ORDERS_KEY, BODY).toBase64());
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(response.getContentAsString()).isEqualTo("error:BODY_TOO_LARGE");
    assertThat(chain.getRequest()).isNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void refusesCallersThatShareKey() {
    Map<String, HmacKey> keysByCaller =
        Map.of("ORDERS", HmacKey.fromUtf8("shared"), "BILLING", HmacKey.fromUtf8("shared"));

    assertThatThrownBy(
            () ->
                new SignatureAuthenticationFilter(
                    keysByCaller, SignatureAuthenticationFilterTest::writeErrorBody))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static void writeErrorBody(HttpServletResponse response, int status, String code)
      throws IOException {
    response.setStatus(status);
    response.getWriter().write("error:" + code);
  }

  private static Stream<Arguments> bodiesExceededMaxSize() {
    return Stream.of(
        Arguments.of("declared one byte over", new DeclaredLengthRequest()),
        Arguments.of("endless without a declared length", new EndlessBodyRequest()));
  }

  private static MockHttpServletRequest request(String signature) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/resource");
    request.setContent(BODY.getBytes(StandardCharsets.UTF_8));
    request.addHeader("X-Outpost-Signature", signature);
    return request;
  }

  private static final class DeclaredLengthRequest extends MockHttpServletRequest {
    DeclaredLengthRequest() {
      super("POST", "/resource");
    }

    @Override
    public long getContentLengthLong() {
      return SizeBoundedRequestBody.MAX_SIZE_BYTES + 1L;
    }

    @Override
    public ServletInputStream getInputStream() {
      throw new AssertionError("a body declared over the maximum size was read");
    }
  }

  private static final class EndlessBodyRequest extends MockHttpServletRequest {
    EndlessBodyRequest() {
      super("POST", "/resource");
    }

    @Override
    public long getContentLengthLong() {
      return -1;
    }

    @Override
    public ServletInputStream getInputStream() {
      return new DelegatingServletInputStream(
          new InputStream() {
            private long bytesRead;

            @Override
            public int read() {
              count(1);
              return 'a';
            }

            @Override
            public int read(byte[] bytes, int offset, int length) {
              count(length);
              Arrays.fill(bytes, offset, offset + length, (byte) 'a');
              return length;
            }

            private void count(int length) {
              bytesRead += length;
              if (bytesRead > SizeBoundedRequestBody.MAX_SIZE_BYTES + 1L) {
                throw new AssertionError("the body was read past the maximum size");
              }
            }
          });
    }
  }
}
